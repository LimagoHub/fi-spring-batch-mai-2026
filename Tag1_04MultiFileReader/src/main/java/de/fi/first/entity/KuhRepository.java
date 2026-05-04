package de.fi.first.entity;


import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface KuhRepository extends CrudRepository<Kuh, UUID> {
}
