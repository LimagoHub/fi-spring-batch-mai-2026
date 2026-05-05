package de.fi.tag2_07partitionierer.batch;

import de.fi.tag2_07partitionierer.db.entity.StudentEntity;
import de.fi.tag2_07partitionierer.mapper.StudentMapper;
import de.fi.tag2_07partitionierer.model.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class StudentItemProcessor implements ItemProcessor<Student, StudentEntity> {

    private static final Logger log = LoggerFactory.getLogger(StudentItemProcessor.class);

    private final StudentMapper studentMapper;

    public StudentItemProcessor(StudentMapper studentMapper) {
        this.studentMapper = studentMapper;
    }

    @Override
    public StudentEntity process(Student student) {
        StudentEntity entity = studentMapper.toEntity(student);
        log.debug("Gemappt: {} {} → {}", student.vorname(), student.nachname(), entity.getId());
        return entity;
    }
}
