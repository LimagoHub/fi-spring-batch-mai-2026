package de.fi.testdemo;


import de.fi.testdemo.entity.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

// Kein @Component: manuell als @Bean deklariert, weil der Processor kein Spring-Kontext
// braucht und sich so im Unit-Test ohne SpringBootTest instanziieren lässt.
public class PersonItemProcessor implements ItemProcessor<Person, Person> {

    private static final Logger log = LoggerFactory.getLogger(PersonItemProcessor.class);

    @Override
    public Person process(final Person person) throws Exception {

        // toUpperCase: Normalisierung vor dem Schreiben, damit kein Mischfall in der DB landet
        final String firstName = person.getFirstName().toUpperCase();
        final String lastName = person.getLastName().toUpperCase();

        // Neues Objekt statt Mutation: ItemProcessor-Kontrakt verlangt Idempotenz —
        // dasselbe Item darf mehrfach verarbeitet werden (Retry) ohne Seiteneffekte
        final Person transformedPerson = new Person(firstName, lastName, 10);

        log.info("Converting (" + person + ") into (" + transformedPerson + ")");

        return transformedPerson;
    }

}