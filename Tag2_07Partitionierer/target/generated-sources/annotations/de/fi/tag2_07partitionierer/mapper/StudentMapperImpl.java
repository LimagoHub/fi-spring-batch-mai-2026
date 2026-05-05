package de.fi.tag2_07partitionierer.mapper;

import de.fi.tag2_07partitionierer.db.entity.StudentEntity;
import de.fi.tag2_07partitionierer.model.Student;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-05T11:13:47+0200",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 25.0.2 (Oracle Corporation)"
)
@Component
public class StudentMapperImpl implements StudentMapper {

    @Override
    public StudentEntity toEntity(Student student) {
        if ( student == null ) {
            return null;
        }

        StudentEntity studentEntity = new StudentEntity();

        studentEntity.setVorname( student.vorname() );
        studentEntity.setNachname( student.nachname() );

        studentEntity.setId( java.util.UUID.randomUUID() );

        return studentEntity;
    }
}
