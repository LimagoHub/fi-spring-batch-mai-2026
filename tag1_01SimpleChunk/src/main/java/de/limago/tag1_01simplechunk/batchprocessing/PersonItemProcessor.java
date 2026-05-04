package de.limago.tag1_01simplechunk.batchprocessing;



// @Slf4j (Lombok) generiert ein statisches Logger-Feld zur Compile-Zeit —
// spart das manuelle LoggerFactory.getLogger(...) in jeder Klasse.

import de.limago.tag1_01simplechunk.entity.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;


public class PersonItemProcessor implements ItemProcessor<Person, Person> {


    Logger logger = LoggerFactory.getLogger(PersonItemProcessor.class);


    // ItemProcessor<Person, Person>: Eingabe- und Ausgabe-Typ sind hier gleich (Person),
    // weil wir dieselbe Klasse transformieren. Sie könnten auch unterschiedlich sein
    // (z. B. <CsvRecord, DatabaseEntity>), wenn Lese- und Schreibe-Modell abweichen.
    @Override
    public Person process(final Person person) {
        final String firstName = person.getFirstName().toUpperCase();
        final String lastName = person.getLastName().toUpperCase();
        // age wird fest auf 10 gesetzt — im Schulungsbeispiel wird das Alter
        // in der Datenbank nicht gespeichert (kein age-Feld im INSERT), aber der
        // Konstruktor erwartet es trotzdem.
        final Person transformedPerson = new Person(firstName, lastName, 10);

        logger.info("Converting ({}) into ({})", person, transformedPerson);
        return transformedPerson;
        // Würde man hier null zurückgeben, filtert Spring Batch diesen Datensatz
        // stillschweigend heraus — er wird nicht an den Writer weitergegeben.
    }
}