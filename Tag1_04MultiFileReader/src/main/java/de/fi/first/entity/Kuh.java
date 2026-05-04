package de.fi.first.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;


@Entity
@Table(name = "tbl_kuehe") // expliziter Tabellenname vermeidet Konflikte mit reservierten SQL-Wörtern
public class Kuh {

    @Id
    // UUID statt generierter Long-ID: ermöglicht clientseitige ID-Vergabe ohne DB-Roundtrip
    private UUID id;
    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int age;

    public Kuh() {
    }

    public Kuh(final UUID id, final String name, final int age) {
        this.id = id;
        this.name = name;
        this.age = age;
    }

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(final int age) {
        this.age = age;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final Kuh kuh = (Kuh) o;
        return age == kuh.age && Objects.equals(id, kuh.id) && Objects.equals(name, kuh.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, age);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Kuh{");
        sb.append("id=").append(id);
        sb.append(", name='").append(name).append('\'');
        sb.append(", age=").append(age);
        sb.append('}');
        return sb.toString();
    }
}
