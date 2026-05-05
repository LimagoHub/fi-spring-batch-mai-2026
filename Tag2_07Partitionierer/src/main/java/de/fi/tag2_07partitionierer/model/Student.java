package de.fi.tag2_07partitionierer.model;

// Reines Transport-Objekt aus der CSV — keine JPA-Annotationen, keine DB-Logik.
// Die Umwandlung nach StudentEntity übernimmt der StudentMapper (MapStruct).
public record Student(String id, String vorname, String nachname) {}
