package pe.edu.utec.devutec.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.model.Skill;
import pe.edu.utec.devutec.repository.SkillRepository;

import java.util.List;

// Catálogo inicial de habilidades, como en las plataformas de freelance: así los clientes eligen de una lista
// común en vez de escribir "react", "React.js" y "ReactJS" por separado. Al iniciar el backend se agregan
// solo las que faltan; no se borra ni se cambia ninguna que ya exista.
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillCatalogInitializer implements ApplicationRunner {

    static final List<String> CATALOG = List.of(
            // Web
            "HTML y CSS", "JavaScript", "TypeScript", "React", "Next.js", "Angular", "Vue.js", "Tailwind CSS",
            "Node.js", "Express",
            // Backend
            "Java", "Spring Boot", "Python", "Django", "FastAPI", "PHP", "Laravel", "C#", ".NET", "Go",
            // Móvil
            "React Native", "Flutter", "Kotlin", "Swift",
            // Datos
            "SQL", "PostgreSQL", "MySQL", "MongoDB", "Firebase", "Excel", "Power BI", "Análisis de datos",
            "Machine Learning",
            // Nube y herramientas
            "AWS", "Docker", "Git", "APIs REST",
            // Diseño y contenido
            "Figma", "Diseño UX/UI", "Diseño gráfico", "Branding", "Edición de video", "Fotografía de producto",
            "Redacción", "SEO", "Redes sociales", "Marketing digital",
            // Negocio digital
            "WordPress", "Shopify", "E-commerce", "Automatización"
    );

    private final SkillRepository skillRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Skill> missing = CATALOG.stream()
                .filter(name -> !skillRepository.existsByNameIgnoreCase(name))
                .map(name -> {
                    Skill skill = new Skill();
                    skill.setName(name);
                    return skill;
                })
                .toList();
        if (!missing.isEmpty()) {
            skillRepository.saveAll(missing);
            log.info("Catálogo de habilidades: se agregaron {}", missing.size());
        }
    }
}
