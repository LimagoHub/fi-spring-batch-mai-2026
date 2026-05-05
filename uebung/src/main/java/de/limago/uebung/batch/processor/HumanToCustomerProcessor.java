package de.limago.uebung.batch.processor;

import de.limago.uebung.entity.Customer;
import de.limago.uebung.entity.Human;
import de.limago.uebung.mapper.HumanCustomerMapper;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class HumanToCustomerProcessor implements ItemProcessor<Human, Customer> {

    private final HumanCustomerMapper mapper;

    public HumanToCustomerProcessor(HumanCustomerMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Customer process(Human human) {
        Customer customer = mapper.toCustomer(human);
        // UUID als synthetischer Primaerschluessel – tbl_humans hat keine eigene ID
        customer.setId(UUID.randomUUID().toString());
        return customer;
    }
}
