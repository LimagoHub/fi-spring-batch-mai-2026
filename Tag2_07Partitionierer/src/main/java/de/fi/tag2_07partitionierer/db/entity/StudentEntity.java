package de.fi.tag2_07partitionierer.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tbl_studenten")
public class StudentEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String vorname;

    @Column(nullable = false, length = 50)
    private String nachname;

    public StudentEntity() {
    }

    public StudentEntity(final UUID id, final String vorname, final String nachname) {
        this.id = id;
        this.vorname = vorname;
        this.nachname = nachname;
    }

    public UUID getId() { return id; }
    public void setId(final UUID id) { this.id = id; }

    public String getVorname() { return vorname; }
    public void setVorname(final String vorname) { this.vorname = vorname; }

    public String getNachname() { return nachname; }
    public void setNachname(final String nachname) { this.nachname = nachname; }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final StudentEntity that = (StudentEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "StudentEntity{id=" + id + ", vorname='" + vorname + "', nachname='" + nachname + "'}";
    }
}
