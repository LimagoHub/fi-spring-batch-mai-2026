package de.fi.tag2_06partitionierer;

// Reines Daten-Transferobjekt (DTO) für die Leseschicht.
// Java Record: alle Felder sind final, equals/hashCode/toString werden automatisch generiert.
// Keinerlei JPA-Annotation — Person hat keine Bedeutung in der Datenbank,
// sie existiert nur als Zwischenobjekt zwischen Reader und Processor.
public record Person(int id, String firstName, String lastName) {}