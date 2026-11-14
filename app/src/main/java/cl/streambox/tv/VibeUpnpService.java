package cl.streambox.tv;

import android.content.Context;

import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.android.AndroidUpnpServiceConfiguration;
import org.jupnp.android.AndroidUpnpServiceImpl;
import org.jupnp.transport.impl.jetty.JettyStreamClientImpl;
import org.jupnp.transport.impl.jetty.StreamClientConfigurationImpl;
import org.jupnp.transport.spi.StreamClient;

import java.util.concurrent.ExecutorService;

/**
 * Servicio jUPnP del Player. Igual que {@link AndroidUpnpServiceImpl}, pero el
 * cliente HTTP (Browse por SOAP) manda el User-Agent de {@link ClientIdentity}
 * en vez del predeterminado de Android (contrato §C).
 */
public final class VibeUpnpService extends AndroidUpnpServiceImpl {
    @Override
    protected UpnpServiceConfiguration createConfiguration() {
        return new VibeUpnpConfiguration(this);
    }

    static final class VibeUpnpConfiguration extends AndroidUpnpServiceConfiguration {
        private final String userAgent;

        VibeUpnpConfiguration(Context context) {
            super();
            this.userAgent = ClientIdentity.forDevice(context);
        }

        @Override
        public StreamClient createStreamClient() {
            ExecutorService executor = getSyncProtocolExecutorService();
            StreamClientConfigurationImpl configuration =
                    new StreamClientConfigurationImpl(executor) {
                        @Override
                        public String getUserAgentValue(int majorVersion, int minorVersion) {
                            return userAgent;
                        }
                    };
            try {
                return new JettyStreamClientImpl(configuration);
            } catch (Exception failure) {
                throw new IllegalStateException("No se pudo crear el cliente HTTP de jUPnP.", failure);
            }
        }
    }
}
