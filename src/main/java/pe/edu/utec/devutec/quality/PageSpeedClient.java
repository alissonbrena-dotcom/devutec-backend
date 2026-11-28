package pe.edu.utec.devutec.quality;

// Mide una web con PageSpeed Insights de Google. Es una interfaz para poder probar sin llamar a Google.
public interface PageSpeedClient {

    // Lanza PageSpeedUnavailableException si Google no respondió (no es culpa del estudiante)
    // y SiteNotReachableException si Google no pudo abrir la web (sí es algo que el estudiante debe arreglar).
    PageSpeedReport analyze(String url);

    class PageSpeedUnavailableException extends RuntimeException {
        public PageSpeedUnavailableException(String message) {
            super(message);
        }
    }

    class SiteNotReachableException extends RuntimeException {
        public SiteNotReachableException(String message) {
            super(message);
        }
    }
}
