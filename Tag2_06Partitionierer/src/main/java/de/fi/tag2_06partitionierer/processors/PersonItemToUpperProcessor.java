package de.fi.tag2_06partitionierer.processors;


import de.fi.tag2_06partitionierer.Person;
import org.slf4j.Logger;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;


// @Component ohne @StepScope ist korrekt, weil dieser Processor zustandslos ist —
// er hält keine Instanzvariablen, die sich zwischen Aufrufen ändern.
// Zustandslose Beans können problemlos von mehreren Threads gleichzeitig genutzt werden.
@Component
public class PersonItemToUpperProcessor implements ItemProcessor<Person, Person> {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(PersonItemToUpperProcessor.class);

    @Override
    public Person process(final Person person) {
        final int id = person.id();
        final String firstName = person.firstName().toUpperCase();
        final String lastName = person.lastName().toUpperCase();
        // Neues Person-Objekt statt Mutation: Records in Java sind unveränderlich,
        // daher muss immer eine neue Instanz mit den transformierten Werten erzeugt werden.
        final Person transformedPerson = new Person(id, firstName, lastName);

        log.info("Converting ({}) into ({})", person, transformedPerson);
        return transformedPerson;
    }
}