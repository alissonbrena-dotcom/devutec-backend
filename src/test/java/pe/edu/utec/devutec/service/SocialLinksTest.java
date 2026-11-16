package pe.edu.utec.devutec.service;

import org.junit.jupiter.api.Test;
import pe.edu.utec.devutec.exceptions.ApiException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialLinksTest {

    @Test
    void instagram_aceptaElUsuarioConOSinArroba() {
        assertEquals("https://instagram.com/pizzeriadonmario", SocialLinks.instagram("@pizzeriadonmario"));
        assertEquals("https://instagram.com/pizzeria.don_mario", SocialLinks.instagram("pizzeria.don_mario"));
    }

    @Test
    void instagram_aceptaElLinkConOSinHttps() {
        assertEquals("https://www.instagram.com/pizzeria", SocialLinks.instagram("https://www.instagram.com/pizzeria"));
        assertEquals("https://instagram.com/pizzeria", SocialLinks.instagram("instagram.com/pizzeria"));
    }

    @Test
    void instagram_rechazaUnLinkDeOtraPagina() {
        assertThrows(ApiException.class, () -> SocialLinks.instagram("https://facebook.com/pizzeria"));
    }

    @Test
    void tiktok_agregaLaArrobaAlUsuario() {
        assertEquals("https://tiktok.com/@pizzeria", SocialLinks.tiktok("@pizzeria"));
    }

    @Test
    void facebook_aceptaLaVersionMovil() {
        assertEquals("https://m.facebook.com/pizzeria", SocialLinks.facebook("https://m.facebook.com/pizzeria"));
    }

    @Test
    void website_agregaHttpsYPideUnDominio() {
        assertEquals("https://pizzeriadonmario.pe", SocialLinks.website("pizzeriadonmario.pe"));
        assertEquals("http://mi-tienda.com/menu", SocialLinks.website("http://mi-tienda.com/menu"));
        assertThrows(ApiException.class, () -> SocialLinks.website("pizzeria"));
    }

    @Test
    void vacioONull_quitaLaRed() {
        assertNull(SocialLinks.instagram("   "));
        assertNull(SocialLinks.website(null));
    }

    @Test
    void conEspacios_esInvalido() {
        assertThrows(ApiException.class, () -> SocialLinks.facebook("pizzeria don mario"));
    }
}
