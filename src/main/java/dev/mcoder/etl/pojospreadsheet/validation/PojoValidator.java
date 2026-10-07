package dev.mcoder.etl.pojospreadsheet.validation;

import java.util.Objects;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Validates POJOs against their Jakarta Bean Validation annotations.
 *
 * <p>Creating a {@link ValidatorFactory} is expensive, so create one {@code PojoValidator}
 * and reuse it. It is thread-safe.
 */
public final class PojoValidator implements AutoCloseable {

    private final ValidatorFactory factory;
    private final Validator validator;
    private final boolean ownsFactory;

    public PojoValidator() {
        this(Validation.buildDefaultValidatorFactory(), true);
    }

    public PojoValidator(ValidatorFactory factory) {
        this(factory, false);
    }

    private PojoValidator(ValidatorFactory factory, boolean ownsFactory) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.validator = factory.getValidator();
        this.ownsFactory = ownsFactory;
    }

    /**
     * Returns every constraint violation of {@code pojo}; the set is empty when it is valid.
     */
    public <T> Set<ConstraintViolation<T>> validate(T pojo, Class<?>... groups) {
        Objects.requireNonNull(pojo, "pojo");
        return validator.validate(pojo, groups);
    }

    /**
     * Returns {@code pojo} when it is valid.
     *
     * @throws ConstraintViolationException listing every violation when it is not
     */
    public <T> T validateOrThrow(T pojo, Class<?>... groups) {
        Set<ConstraintViolation<T>> violations = validate(pojo, groups);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return pojo;
    }

    /**
     * Closes the factory if this instance created it; a factory passed in by the caller is left open.
     */
    @Override
    public void close() {
        if (ownsFactory) {
            factory.close();
        }
    }
}
