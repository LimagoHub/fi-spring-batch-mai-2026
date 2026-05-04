package de.fi.first.batchprocessing;


import de.fi.first.entity.Kuh;
import de.fi.first.entity.KuhRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class Demo {

    private final KuhRepository kuhRepository;

    public Demo(final KuhRepository kuhRepository) {
        this.kuhRepository = kuhRepository;
    }

    @PostConstruct
    // @PostConstruct läuft nach der Dependency-Injection, aber noch vor dem ersten HTTP-Request
    // und vor CommandLineRunner — so ist die Datenbank bereits befüllt, wenn der Batch-Job startet
    public void savekuh() {
        // UUID.randomUUID() als PK: kein Auto-Increment nötig, funktioniert über DB-Grenzen hinweg
        Kuh elsa = new Kuh(UUID.randomUUID(), "Elsa",10);
        kuhRepository.save(elsa);
    }
}
