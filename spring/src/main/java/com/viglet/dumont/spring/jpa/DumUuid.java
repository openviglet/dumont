package com.viglet.dumont.spring.jpa;

import org.hibernate.annotations.IdGeneratorType;
import org.hibernate.annotations.ValueGenerationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@IdGeneratorType(DumUuidGenerator.class)
@ValueGenerationType(generatedBy = DumUuidGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
public @interface DumUuid {
    org.hibernate.annotations.UuidGenerator.Style style() default org.hibernate.annotations.UuidGenerator.Style.AUTO;

    enum Style {
        AUTO,
        RANDOM,
        TIME;

        private Style() {
        }
    }
}
