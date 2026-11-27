package pe.edu.utec.devutec.translation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.model.FreelancerProfile;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.service.CurrentUserService;
import pe.edu.utec.devutec.support.AiGateway;
import pe.edu.utec.devutec.support.ChatTurnDTO;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TranslationServiceTest {

    private static final String CLIENT_REPLY = """
            ```json
            {"fuera_de_tema": false,
             "en_simple": "Tus clientes ya pueden pedir pizzas, pero la web aún no está publicada.",
             "ya_esta": ["Tus clientes pueden elegir pizzas y agregarlas al carrito"],
             "falta": ["Publicar la web en internet", "  "],
             "revisa_esto": ["Mira si los precios están bien"],
             "terminos": [{"termino": "deploy", "significado": "publicar la web en internet"}, {"termino": ""}]}
            ```""";

    private static final String STUDENT_REPLY = """
            {"fuera_de_tema": false,
             "en_simple": "Mario quiere un estilo más alegre.",
             "tareas": ["Usar colores más vivos en los botones"],
             "preguntas": ["¿Tienes alguna web de ejemplo que te guste?"],
             "ojo": ""}""";

    // IA falsa: guarda lo que recibió y responde lo que se le diga
    static class FakeAi implements AiGateway {
        String instructions;
        List<ChatTurnDTO> turns;
        String reply;
        int calls;

        @Override
        public String reply(String instructions, List<ChatTurnDTO> turns) {
            this.instructions = instructions;
            this.turns = turns;
            calls++;
            return reply;
        }
    }

    private final FakeAi ai = new FakeAi();
    private final ContractRepository contracts = mock(ContractRepository.class);
    private final TranslationRepository translations = mock(TranslationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T15:00:00Z"), ZoneId.of("America/Lima"));

    private User client;
    private User student;
    private Contract contract;

    @BeforeEach
    void setUp() {
        client = user(1L, "Mario", Role.CLIENT);
        student = user(2L, "Sofía", Role.FREELANCER);

        FreelancerProfile profile = new FreelancerProfile();
        profile.setUser(student);
        Project project = new Project();
        project.setTitle("Web para Pizzería Mario");
        project.setDescription("Carta y pedidos en línea");
        project.setClientId(client.getId());
        Application application = new Application();
        application.setProject(project);
        application.setFreelancer(profile);
        contract = new Contract();
        contract.setId(50L);
        contract.setApplication(application);
        contract.setDeliveryNote("Implementé el carrito con Context. Falta el deploy en Vercel.");

        when(contracts.findById(50L)).thenReturn(Optional.of(contract));
        when(users.findById(1L)).thenReturn(Optional.of(client));
        when(translations.findFirstByContract_IdAndDirectionAndOriginalHash(anyLong(), any(), any()))
                .thenReturn(Optional.empty());
        when(translations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private TranslationService service(int limit) {
        return new TranslationService(ai, contracts, translations, users, currentUser, limit, clock);
    }

    private static User user(Long id, String name, Role role) {
        User user = new User();
        user.setId(id);
        user.setNombre(name);
        user.setRol(role);
        return user;
    }

    @Test
    void paraElCliente_sinTexto_traduceLaNotaDeEntregaYOrdenaLaRespuesta() {
        when(currentUser.getCurrentUser()).thenReturn(client);
        ai.reply = CLIENT_REPLY;

        TranslationResponseDTO response = service(40).translate(50L,
                new TranslateRequestDTO(TranslationDirection.FOR_CLIENT, null, "Diseño"));

        TranslationContent result = response.result();
        assertEquals("Tus clientes ya pueden pedir pizzas, pero la web aún no está publicada.", result.enSimple());
        assertEquals(List.of("Tus clientes pueden elegir pizzas y agregarlas al carrito"), result.yaEsta());
        assertEquals(List.of("Publicar la web en internet"), result.falta());
        assertEquals(List.of(new TranslationContent.Term("deploy", "publicar la web en internet")), result.terminos());
        assertEquals(contract.getDeliveryNote(), response.original());
        assertEquals("Diseño", response.stageName());
        // La IA recibe el contexto del proyecto y el mensaje marcado como texto a traducir
        assertTrue(ai.instructions.contains("Web para Pizzería Mario"));
        assertTrue(ai.instructions.contains("Cliente: Mario. Estudiante: Sofía. Etapa: Diseño."));
        assertTrue(ai.instructions.contains("NO sabe de programación"));
        assertTrue(ai.turns.get(0).text().startsWith("<mensaje>"));
        verify(translations).save(any());
    }

    @Test
    void paraElEstudiante_devuelveTareasYPreguntas() {
        when(currentUser.getCurrentUser()).thenReturn(student);
        ai.reply = STUDENT_REPLY;

        TranslationContent result = service(40).translate(50L,
                new TranslateRequestDTO(TranslationDirection.FOR_STUDENT, "Quiero que se vea más alegre", null)).result();

        assertEquals(List.of("Usar colores más vivos en los botones"), result.tareas());
        assertEquals(List.of("¿Tienes alguna web de ejemplo que te guste?"), result.preguntas());
        assertTrue(result.yaEsta().isEmpty());
        assertTrue(ai.instructions.contains("qué pide el cliente"));
    }

    @Test
    void siYaSeTradujo_devuelveLaGuardadaSinLlamarALaIa() {
        when(currentUser.getCurrentUser()).thenReturn(client);
        Translation saved = new Translation();
        saved.setDirection(TranslationDirection.FOR_STUDENT);
        saved.setOriginal("Hola");
        saved.setContent("{\"enSimple\":\"Guardada\",\"tareas\":[],\"preguntas\":[],\"ojo\":\"\"}");
        when(translations.findFirstByContract_IdAndDirectionAndOriginalHash(eq(50L),
                eq(TranslationDirection.FOR_STUDENT), any())).thenReturn(Optional.of(saved));

        TranslationResponseDTO response = service(40).translate(50L,
                new TranslateRequestDTO(TranslationDirection.FOR_STUDENT, "Hola", null));

        assertEquals("Guardada", response.result().enSimple());
        assertEquals(0, ai.calls);
    }

    @Test
    void fueraDeTema_respondeUnMensajeFijo() {
        when(currentUser.getCurrentUser()).thenReturn(client);
        ai.reply = "{\"fuera_de_tema\": true}";

        ApiException error = assertThrows(ApiException.class, () -> service(40).translate(50L,
                new TranslateRequestDTO(TranslationDirection.FOR_STUDENT, "Dame una receta de pizza", null)));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        assertEquals(TranslationService.OFF_TOPIC_MESSAGE, error.getMessage());
        verify(translations, never()).save(any());
    }

    @Test
    void siLaIaNoRespondeJson_noSeGuardaYNoCuentaParaElLimite() {
        when(currentUser.getCurrentUser()).thenReturn(client);
        TranslationService service = service(1);
        ai.reply = "Lo siento, no puedo";

        ApiException error = assertThrows(ApiException.class, () -> service.translate(50L,
                new TranslateRequestDTO(TranslationDirection.FOR_STUDENT, "Cambia el logo", null)));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());

        // La traducción fallida se devolvió al límite, así que todavía queda una
        ai.reply = STUDENT_REPLY;
        assertDoesNotThrow(() -> service.translate(50L,
                new TranslateRequestDTO(TranslationDirection.FOR_STUDENT, "Cambia el logo", null)));
    }

    @Test
    void alPasarElLimiteDelDia_respondeTooManyRequests() {
        when(currentUser.getCurrentUser()).thenReturn(client);
        ai.reply = STUDENT_REPLY;
        TranslationService service = service(1);
        service.translate(50L, new TranslateRequestDTO(TranslationDirection.FOR_STUDENT, "Uno", null));

        ApiException error = assertThrows(ApiException.class, () -> service.translate(50L,
                new TranslateRequestDTO(TranslationDirection.FOR_STUDENT, "Dos", null)));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());
    }

    @Test
    void quienNoEsDelContrato_noPuedeTraducir() {
        when(currentUser.getCurrentUser()).thenReturn(user(9L, "Otra", Role.CLIENT));

        assertThrows(ForbiddenOperationException.class, () -> service(40).translate(50L,
                new TranslateRequestDTO(TranslationDirection.FOR_CLIENT, "Hola", null)));
        assertEquals(0, ai.calls);
    }

    @Test
    void paraElEstudiante_sinTexto_respondeBadRequest() {
        when(currentUser.getCurrentUser()).thenReturn(student);

        ApiException error = assertThrows(ApiException.class, () -> service(40).translate(50L,
                new TranslateRequestDTO(TranslationDirection.FOR_STUDENT, "  ", null)));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }
}
