package de.fi.tag2_06partitionierer.processors;

import de.fi.tag2_06partitionierer.Person;
import de.fi.tag2_06partitionierer.db.entity.StudentEntity;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.support.CompositeItemProcessor;
import org.springframework.stereotype.Component;

import java.util.List;


// Pipeline: Person → (Uppercase) → Person → (Mapping) → StudentEntity
// CompositeItemProcessor leitet das Ergebnis jedes Delegates als Eingabe an den nächsten weiter.
@Component
public class MyCompositeProcessor extends CompositeItemProcessor<Person, StudentEntity> {

    private final PersonItemToUpperProcessor personItemToUpperProcessor;
    private final PersonToStudentProcessor personToStudentProcessor;

    public MyCompositeProcessor(final PersonItemToUpperProcessor personItemToUpperProcessor, final PersonToStudentProcessor personToStudentProcessor) {
        this.personItemToUpperProcessor = personItemToUpperProcessor;
        this.personToStudentProcessor = personToStudentProcessor;
        // Reihenfolge ist entscheidend: erst Uppercase (Person→Person), dann Mapping (Person→StudentEntity).
        // Vertauscht würde personToStudentProcessor eine StudentEntity übergeben bekommen, was einen Typfehler auslöst.
        // setDelegates im Konstruktor, weil CompositeItemProcessor.process() die Liste sofort beim ersten Aufruf benötigt.
        super.setDelegates(List.of(personItemToUpperProcessor, personToStudentProcessor));
    }


}
