package de.limago.uebung.mapper;

import de.limago.uebung.entity.Customer;
import de.limago.uebung.entity.Human;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface HumanCustomerMapper {

    // id wird im Processor per UUID gesetzt, nicht hier
    @Mapping(target = "id", ignore = true)
    Customer toCustomer(Human human);
}
