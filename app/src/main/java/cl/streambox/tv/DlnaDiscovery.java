package cl.streambox.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.jupnp.android.AndroidUpnpService;
import org.jupnp.android.AndroidUpnpServiceImpl;
import org.jupnp.controlpoint.ActionCallback;
import org.jupnp.model.message.header.UDADeviceTypeHeader;
import org.jupnp.model.meta.Device;
import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.Service;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDAServiceId;
import org.jupnp.registry.DefaultRegistryListener;
import org.jupnp.registry.Registry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DlnaDiscovery implements AutoCloseable {
    private static final long SEARCH_INTERVAL_MS = 5L * 60L * 1_000L;
    private static final UDADeviceType MEDIA_SERVER = new UDADeviceType("MediaServer");
    private static final UDAServiceId CONTENT_DIRECTORY =
            new UDAServiceId("ContentDirectory");

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object monitor = new Object();
    private final Map<String, DlnaServer> servers = new LinkedHashMap<>();

    private AndroidUpnpService upnpService;
    private boolean bound;
    private boolean closed;

    private final Runnable periodicSearch = new Runnable() {
        @Override
        public void run() {
            AndroidUpnpService service;
            synchronized (monitor) {
                if (closed) return;
                service = upnpService;
            }
            if (service != null) {
                search(service);
            }
            mainHandler.postDelayed(this, SEARCH_INTERVAL_MS);
        }
    };

    private final DefaultRegistryListener registryListener =
            new DefaultRegistryListener() {
                @Override
                public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
                    addDevice(device);
                }

                @Override
                public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
                    removeDevice(device);
                }

                @Override
                public void localDeviceAdded(Registry registry, LocalDevice device) {
                    addDevice(device);
                }

                @Override
                public void localDeviceRemoved(Registry registry, LocalDevice device) {
                    removeDevice(device);
                }
            };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            AndroidUpnpService connected = (AndroidUpnpService) binder;
            synchronized (monitor) {
                if (closed) return;
                upnpService = connected;
            }
            try {
                connected.get().startup();
                connected.getRegistry().addListener(registryListener);
                for (Device<?, ?, ?> device : connected.getRegistry().getDevices()) {
                    addDevice(device);
                }
                search(connected);
                mainHandler.removeCallbacks(periodicSearch);
                mainHandler.postDelayed(periodicSearch, SEARCH_INTERVAL_MS);
            } catch (RuntimeException startupFailure) {
                synchronized (monitor) {
                    if (upnpService == connected) {
                        upnpService = null;
                        servers.clear();
                    }
                }
            } finally {
                synchronized (monitor) {
                    monitor.notifyAll();
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            clearService();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            clearService();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            clearService();
        }
    };

    DlnaDiscovery(Context context) {
        this.context = context.getApplicationContext();
        Intent intent = new Intent(this.context, AndroidUpnpServiceImpl.class);
        bound = this.context.bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
        );
    }

    List<DlnaServer> discover(long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        AndroidUpnpService service = awaitService(deadline);
        if (service == null) return snapshot();

        for (Device<?, ?, ?> device : service.getRegistry().getDevices()) {
            addDevice(device);
        }
        search(service);

        synchronized (monitor) {
            while (!closed) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) break;
                try {
                    monitor.wait(remaining);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return snapshot();
    }

    void execute(ActionCallback callback) throws IOException {
        AndroidUpnpService service;
        synchronized (monitor) {
            service = upnpService;
        }
        if (service == null) {
            throw new IOException("El servicio UPnP no está disponible.");
        }
        callback.setControlPoint(service.getControlPoint());
        callback.run();
    }

    private AndroidUpnpService awaitService(long deadline) {
        synchronized (monitor) {
            while (!closed && upnpService == null) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) break;
                try {
                    monitor.wait(remaining);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return upnpService;
        }
    }

    private void addDevice(Device<?, ?, ?> device) {
        Service<?, ?> service = device.findService(CONTENT_DIRECTORY);
        if (service == null || service.getAction("Browse") == null) return;

        String udn = device.getIdentity().getUdn().toString();
        String friendlyName = device.getDetails() == null
                ? null
                : device.getDetails().getFriendlyName();
        if (friendlyName == null || friendlyName.isBlank()) {
            friendlyName = device.getDisplayString();
        }
        synchronized (monitor) {
            if (closed) return;
            servers.put(udn, new DlnaServer(udn, friendlyName, service));
            monitor.notifyAll();
        }
    }

    private void removeDevice(Device<?, ?, ?> device) {
        String udn = device.getIdentity().getUdn().toString();
        synchronized (monitor) {
            servers.remove(udn);
            monitor.notifyAll();
        }
    }

    private static void search(AndroidUpnpService service) {
        try {
            service.getControlPoint().search(
                    new UDADeviceTypeHeader(MEDIA_SERVER)
            );
        } catch (RuntimeException ignored) {
            // Una reconexión de red puede invalidar temporalmente el router UPnP.
        }
    }

    private List<DlnaServer> snapshot() {
        List<DlnaServer> result;
        synchronized (monitor) {
            result = new ArrayList<>(servers.values());
        }
        Collections.sort(result, new Comparator<DlnaServer>() {
            @Override
            public int compare(DlnaServer left, DlnaServer right) {
                return left.getFriendlyName().compareToIgnoreCase(
                        right.getFriendlyName()
                );
            }
        });
        return result;
    }

    private void clearService() {
        mainHandler.removeCallbacks(periodicSearch);
        synchronized (monitor) {
            upnpService = null;
            servers.clear();
            monitor.notifyAll();
        }
    }

    @Override
    public void close() {
        AndroidUpnpService service;
        boolean shouldUnbind;
        synchronized (monitor) {
            if (closed) return;
            closed = true;
            service = upnpService;
            upnpService = null;
            servers.clear();
            shouldUnbind = bound;
            bound = false;
            monitor.notifyAll();
        }
        if (service != null) {
            service.getRegistry().removeListener(registryListener);
        }
        mainHandler.removeCallbacks(periodicSearch);
        if (shouldUnbind) {
            try {
                context.unbindService(serviceConnection);
            } catch (IllegalArgumentException ignored) {
                // El sistema ya había desconectado el servicio.
            }
        }
    }
}
