package de.fi.tag2_06partitionierer.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

// @Entity und @Table trennen den Java-Klassennamen vom Datenbanktabellennamen.
// "tbl_studenten" macht die Tabelle als Batch-Ausgabetabelle erkennbar
// und vermeidet Namenskollisionen mit anderen "studenten"-Tabellen in der DB.
@Entity
@Table(name = "tbl_studenten")
public class StudentEntity {

    // UUID als Primärschlüssel statt einer Auto-Increment-Integer-ID:
    // Bei paralleler Einfügung aus mehreren Threads wäre eine DB-seitige Sequenz ein
    // Engpass. UUID.randomUUID() wird im Processor generiert — keine DB-Koordination nötig.
    @Id
    private UUID id;
    // nullable = false erzwingt Pflichtfelder auf DB-Ebene, nicht nur in Java —
    // damit werden fehlerhafte Datensätze bereits beim Schreiben abgewiesen.
    @Column(nullable = false, length = 50)
    private String vorname;
    @Column(nullable = false, length = 50)
    private String nachname;

    // Parameterloser Konstruktor ist für JPA/Hibernate zwingend, da der ORM
    // beim Lesen Objekte durch Reflection instanziiert und danach die Felder setzt.
    public StudentEntity() {
    }

    public StudentEntity(final UUID id, final String vorname, final String nachname) {
        this.id = id;
        this.vorname = vorname;
        this.nachname = nachname;
    }


    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public String getVorname() {
        return vorname;
    }

    public void setVorname(final String vorname) {
        this.vorname = vorname;
    }

    public String getNachname() {
        return nachname;
    }

    public void setNachname(final String nachname) {
        this.nachname = nachname;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("StudentEntity{" );
        sb.append("id=" ).append(id);
        sb.append(", vorname='" ).append(vorname).append('\'');
        sb.append(", nachname='" ).append(nachname).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final StudentEntity that = (StudentEntity) o;
        return Objects.equals(id, that.id) && Objects.equals(vorname, that.vorname) && Objects.equals(nachname, that.nachname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, vorname, nachname);
    }
}
