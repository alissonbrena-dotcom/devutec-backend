package pe.edu.utec.devutec.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.service.ContractStagePlan;

import java.util.List;

// Los contratos creados antes de las etapas no tienen ninguna. Al iniciar el backend se les crea una sola etapa
// con el 100 % del monto, en el estado que corresponde al contrato. Los que ya tienen etapas no se tocan.
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractStageInitializer implements ApplicationRunner {

    private final ContractRepository contractRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Contract> withoutStages = contractRepository.findAll().stream()
                .filter(contract -> contract.getStages().isEmpty())
                .toList();
        withoutStages.forEach(ContractStagePlan::addSingleStage);
        if (!withoutStages.isEmpty()) {
            contractRepository.saveAll(withoutStages);
            log.info("Se creó la etapa única para {} contratos anteriores", withoutStages.size());
        }
    }
}
