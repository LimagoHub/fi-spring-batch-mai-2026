package de.fi.tag2_07partitionierer.mapper;

import de.fi.tag2_07partitionierer.db.entity.StudentEntity;
import de.fi.tag2_07partitionierer.model.Student;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StudentMapper {

    // id aus der CSV wird nicht übernommen — die StudentEntity bekommt eine eigene UUID.
    // MapStruct generiert zur Compile-Zeit eine StudentMapperImpl als Spring-Bean.
    @Mapping(target = "id", expression = "java(java.util.UUID.randomUUID())")
    StudentEntity toEntity(Student student);
}
