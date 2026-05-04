package de.fi.testdemo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Lombok erspart Boilerplate: Getter, Setter, equals, hashCode, toString werden generiert
@Data
@NoArgsConstructor   // Pflicht für den BeanWrapperFieldSetMapper beim CSV-Einlesen
@AllArgsConstructor
@Builder             // Praktisch im Test, um Testdaten fluent aufzubauen
public class Person {
    private String lastName;
    private String firstName;
    private Integer age;
}
