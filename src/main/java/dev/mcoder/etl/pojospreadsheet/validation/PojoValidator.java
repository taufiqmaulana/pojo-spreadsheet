package dev.mcoder.etl.pojospreadsheet.validation;

import java.util.Objects;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Validates POJOs against their Jakarta Bean Validation annotations, including nested
 * {@code @Valid} objects and constraints on container elements such as {@code List<@Email String>}.
 *
 * <pre>{@code
 * try (PojoValidator validator = new PojoValidator()) {
 *     Set<ConstraintViolation<Employee>> violations = validator.validate(employee);
 *     validator.validateOrThrow(employee);   // or throw ConstraintViolationException
 * }
 * }</pre>
 *
 * <p>Creating a {@link ValidatorFactory} is expensive, so create one {@code PojoValidator}
 * and reuse it. It is thread-safe. Close it when the application shuts down.
 */
public final class PojoValidator implements AutoCloseable {

    private final ValidatorFactory factory;
    private final Validator validator;
    private final boolean ownsFactory;

    /**
     * Uses a new default {@link ValidatorFactory} (Hibernate Validator), which {@link #close()} closes.
     */
    public PojoValidator() {
        this(Validation.buildDefaultValidatorFactory(), true);
    }

    /**
     * Uses {@code factory}, for example one configured by a framework such as Spring, with custom
     * message interpolation or constraint validators. {@link #close()} leaves it open.
     */
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
     *
     * @param pojo   the object to validate
     * @param groups validation groups to check; none means the {@code Default} group
     * @throws NullPointerException if {@code pojo} is {@code null}
     */
    public <T> Set<ConstraintViolation<T>> validate(T pojo, Class<?>... groups) {
        Objects.requireNonNull(pojo, "pojo");
        return validator.validate(pojo, groups);
    }

    /**
     * Returns {@code pojo} when it is valid.
     *
     * @param pojo   the object to validate
     * @param groups validation groups to check; none means the {@code Default} group
     * @return {@code pojo}, for chaining
     * @throws ConstraintViolationException listing every violation when it is not
     * @throws NullPointerException         if {@code pojo} is {@code null}
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
