package edu.uclm.es.gramola;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

import java.time.Duration;
import java.util.HashMap;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

/**
 * PRUEBAS FUNCIONALES - LA GRAMOLA
 * Cumple con los requisitos de la sección "4 Prueba funcional":
 * 1) Búsqueda, pago exitoso (4242...) y verificación en BD (tabla tracks).
 * 2) Búsqueda, error de pago (tarjeta declinada) y mensaje en la interfaz.
 * Se utiliza @TestMethodOrder para asegurar que los escenarios se ejecutan en el 
 * orden en el que se nos pide en el enunciado.
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FunctionalTests {

    private WebDriver driver;
    private WebDriverWait wait;
    
    // Configuración JDBC: Necesaria para validar la persistencia real en el backend
    private final String DB_URL = "jdbc:mysql://127.0.0.1:3306/gramola?serverTimezone=UTC&useSSL=false";
    private final String DB_USER = "root";
    private final String DB_PASS = "1234";

    // Datos dinámicos recuperados de la BD antes de cada test
    private String userEmail;
    private String spotifyToken;
    private final String APP_PASSWORD = "12345678";

    /**
     * CONFIGURACIÓN PREVIA A CADA TEST.
     * Prepara el entorno del navegador y recupera credenciales válidas.
    */
    @BeforeEach
    public void setUp() throws Exception {
        // 1. Cargamos un usuario real con token de Spotify de la BD para automatizar el flujo
        fetchRealUserWithToken();

        // 2. Configuración automática del driver de Chrome
        WebDriverManager.chromedriver().setup();
        
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--remote-allow-origins=*");
        
        // Desactivamos automatismos de Chrome que bloquean el test
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-save-password-bubble");
        // Desactiva específicamente la función de detección de contraseñas filtradas (el aviso de "Cambia tu contraseña")
        options.addArguments("--disable-features=PasswordLeakDetection,AutofillPasswordLeakDetection");
        
        Map<String, Object> prefs = new HashMap<String, Object>();

        
        // 2. Desactivar por completo el gestor de contraseñas y la detección de brechas
        prefs.put("credentials_enable_service", false);
        prefs.put("password_manager_enabled", false);
        prefs.put("profile.password_manager_leak_detection", false);
        prefs.put("autofill.profile_enabled", false);
        prefs.put("autofill.credit_card_enabled", false);
        
        options.setExperimentalOption("prefs", prefs);
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        
        this.driver = new ChromeDriver(options);
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(30));
    }

    /**
     * CIERRE DEL ENTORNO.
     * Asegura que el navegador se cierre al finalizar, liberando memoria.
    */
    @AfterEach
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    /**
     * ESCENARIO 1: Pago Exitoso y Registro en Backend.
     * Un cliente busca una canción, paga correctamente y se verifica 
     * que la canción se añade a la lista del bar en la base de datos.
     */
    @Test
    @Order(1)
    public void test1_SearchAndPaySongSuccess() throws Exception {
        flowLoginAndOpenGramola();

        // 1. BUSQUEDA: Localizamos el input de búsqueda y solicitamos una canción específica
        System.out.println("[DEBUG] Buscando canción...");
        WebElement searchInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("input[placeholder*='Busca']")));
        searchInput.clear();
        searchInput.sendKeys("Pájaros de Barro Manolo García");
        slowDown(1000);
        driver.findElement(By.className("btn-search")).click();
        slowDown(1500);

        // 2. SELECCIÓN: Elegimos la canción de los resultados y pulsamos el botón de pago
        WebElement trackCard = wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("track-card")));
        String trackTitle = trackCard.findElement(By.className("track-name")).getText();
        System.out.println("[DEBUG] Seleccionada: " + trackTitle);
        trackCard.findElement(By.className("btn-play")).click();
        slowDown(1000);

        // Cerrar alerta de geolocalización (Modo Pruebas) si aparece
        handleLocationAlertIfPresent();

        // 3. PASARELA STRIPE: Automatización de la introducción de tarjeta
        wait.until(ExpectedConditions.urlContains("/payments"));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("card-element")));
        slowDown(300); 
        
        System.out.println("[DEBUG] Entrando en Stripe...");
        wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.cssSelector("iframe[name^='__privateStripeFrame']")));
        
        WebElement cardInput = wait.until(ExpectedConditions.elementToBeClickable(By.name("cardnumber")));
        cardInput.click();
        
        // Tecleo rápido del número de prueba 4242
        System.out.println("[DEBUG] Tecleando tarjeta...");
        for(int i=0; i<4; i++) {
            cardInput.sendKeys("4242");
            slowDown(50);
        }
        
        driver.findElement(By.name("exp-date")).sendKeys("1228");
        slowDown(500);
        driver.findElement(By.name("cvc")).sendKeys("123");
        slowDown(500);
        driver.findElement(By.name("postal")).sendKeys("13001");
        slowDown(1000);
        
        // Salimos del iframe para pulsar el botón de confirmación de nuestra App
        driver.switchTo().defaultContent();
        System.out.println("[DEBUG] Confirmando pago...");
        driver.findElement(By.id("submit")).click();

        // 4. VERIFICACIÓN INTERFAZ DE USUARIO: Esperamos que el sistema nos devuelva a la gramola y muestre éxito
        wait.until(ExpectedConditions.urlContains("/gramola"));
        slowDown(1500);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("success-banner")));
        slowDown(2000);
        
        // 5. VALIDACIÓN EN BD (REQUISITO: Comprobar que el pago se confirmó y se añadió a la cola)
        System.out.println("[DEBUG] Verificando en MySQL...");
        Thread.sleep(4000); 
        assertTrue(isSongInDatabase(trackTitle), "La canción no se registró en el historial de la BD tras el pago.");
        System.out.println("[SUCCESS] Escenario 1 completado: Canción guardada en backend.");
    }

    /**
     * ESCENARIO 2: Error en datos de pago.
     * Un cliente busca una canción e introduce mal los datos de pago,
     * produciéndose un error visible en la interfaz.
     */
    @Test
    @Order(2)
    public void test2_PaymentErrorScenario() {
        flowLoginAndOpenGramola();

        // 1. Buscamos y seleccionamos una canción distinta
        System.out.println("[DEBUG] Probando error de pago...");
        WebElement searchInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("input[placeholder*='Busca']")));
        searchInput.clear();
        searchInput.sendKeys("Amor Planetario Manuel Carrasco");
        slowDown(1000);
        driver.findElement(By.className("btn-search")).click();
        slowDown(1500);
        
        WebElement trackCard = wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("track-card")));
        trackCard.findElement(By.className("btn-play")).click();
        

        handleLocationAlertIfPresent();

        // 2. Pasarela Stripe con error forzado (tarjeta 0002)
        wait.until(ExpectedConditions.urlContains("/payments"));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("card-element")));
        slowDown(100);
        
        wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.cssSelector("iframe[name^='__privateStripeFrame']")));
        
        WebElement cardInput = wait.until(ExpectedConditions.elementToBeClickable(By.name("cardnumber")));
        cardInput.click();
        
        // Teclear tarjeta declinada
        cardInput.sendKeys("4000"); slowDown(200);
        cardInput.sendKeys("0000"); slowDown(200);
        cardInput.sendKeys("0000"); slowDown(200);
        cardInput.sendKeys("0002");
        
        driver.findElement(By.name("exp-date")).sendKeys("0130");
        slowDown(500);
        driver.findElement(By.name("cvc")).sendKeys("999");
        slowDown(500);
        driver.findElement(By.name("postal")).sendKeys("13001");
        slowDown(1000);
        
        driver.switchTo().defaultContent();
        driver.findElement(By.id("submit")).click();

        // 3. VERIFICACIÓN: Comprobamos que aparece el mensaje de error en la interfaz
        System.out.println("[DEBUG] Comprobando mensaje de error...");
        WebElement errorMsg = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("card-error")));
        slowDown(2000);
        assertFalse(errorMsg.getText().isEmpty(), "No se mostró el mensaje de error esperado tras el fallo en el pago.");
        System.out.println("[SUCCESS] Escenario 2 completado: Error detectado correctamente.");
    }

    /**
     * MÉTODOS DE APOYO Y UTILIDADES
     */

    /** Pausa la ejecución un número determinado de milisegundos */
    private void slowDown(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {}
    }

    /** Recupera un usuario con sesión activa de Spotify desde la Base de Datos */
    private void fetchRealUserWithToken() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {
            // Buscamos un usuario que tenga token de Spotify para evitar el login externo en el test
            ResultSet rs = stmt.executeQuery("SELECT email, spotify_access_token FROM users WHERE spotify_access_token IS NOT NULL LIMIT 1");
            if (rs.next()) {
                this.userEmail = rs.getString("email");
                this.spotifyToken = rs.getString("spotify_access_token");
            } else {
                throw new RuntimeException("Error: No se encontró ningún usuario con token de Spotify en la BD. Realiza un login manual primero.");
            }
        }
    }

    /** Acepta las alertas de geolocalización del navegador si salen */
    private void handleLocationAlertIfPresent() {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(4));
            shortWait.until(ExpectedConditions.alertIsPresent());
            slowDown(1000);
            driver.switchTo().alert().accept();
            System.out.println("[DEBUG] Alerta de geolocalización aceptada.");
            slowDown(1000);
        } catch (Exception e) {
            // Si no aparece la alerta, no hay problema
        }
    }

    /**
     * FLUJO COMPLETO DE ACCESO:
     * Realiza login -> Inyecta Token en LocalStorage -> Salta a la Gramola.
     * Esta técnica evita tener que automatizar la ventana externa de Spotify.
    */
    private void flowLoginAndOpenGramola() {
        driver.get("http://127.0.0.1:4200/login");
        slowDown(1000);
        WebElement emailIn = wait.until(ExpectedConditions.elementToBeClickable(By.id("email")));
        emailIn.sendKeys(this.userEmail);
        slowDown(500);
        driver.findElement(By.id("pwd")).sendKeys(this.APP_PASSWORD);
        slowDown(800);
        driver.findElement(By.className("btn-primary")).click();

        // Inyectamos el token de Spotify recuperado de la BD en el localStorage
        slowDown(2000);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("localStorage.setItem('spotyToken', '" + this.spotifyToken + "');");
        
        // Navegamos al panel y lanzamos la gramola
        driver.get("http://127.0.0.1:4200/music");
        wait.until(ExpectedConditions.urlContains("/music"));
        slowDown(1500);
        WebElement btnGramola = wait.until(ExpectedConditions.elementToBeClickable(By.className("btn-gramola-launch")));
        js.executeScript("arguments[0].scrollIntoView(true);", btnGramola);
        slowDown(1000);
        btnGramola.click();
        wait.until(ExpectedConditions.urlContains("/gramola"));
        slowDown(1000);
    }

    /** Consulta MySQL para verificar si un título de canción ha sido registrado */
    private boolean isSongInDatabase(String title) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {
            // Buscamos si el título de la canción existe en la tabla tracks del backend
            String query = "SELECT count(*) FROM tracks WHERE title LIKE '%" + title + "%'";
            ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) return rs.getInt(1) > 0;
        }
        return false;
    }
}