package de.fi.testdemo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ausgabe-Typ des Processors: bewusst anders als der Eingabe-Typ (Person),
// um zu zeigen dass Reader, Processor und Writer unterschiedliche Typen verwenden dürfen.
// age fehlt hier: nach der Transformation wird das Alter nicht mehr benötigt.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Student {
    private String lastName;
    private String firstName;

}
