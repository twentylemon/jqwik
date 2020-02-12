package net.jqwik.engine.properties;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.*;
import net.jqwik.engine.*;
import net.jqwik.engine.execution.*;
import net.jqwik.engine.support.*;

import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;

class ResolvingParametersTests {

	@Example
	void nothingToResolve() {
		List<MethodParameter> propertyParameters = TestHelper.getParametersFor(TestContainer.class, "nothingToInject");
		Iterator<List<Shrinkable<Object>>> forAllGenerator = shrinkablesIterator(asList(1), asList(2));
		ResolvingParametersGenerator generator = new ResolvingParametersGenerator(
			propertyParameters,
			forAllGenerator,
			ResolveParameterHook.DO_NOT_RESOLVE
		);

		assertThat(generator.hasNext()).isTrue();
		assertThat(toValues(generator.next())).containsExactly(1);
		assertThat(generator.hasNext()).isTrue();
		assertThat(toValues(generator.next())).containsExactly(2);
		assertThat(generator.hasNext()).isFalse();
	}

	@Example
	void resolveLastPosition() {
		List<MethodParameter> propertyParameters = TestHelper.getParametersFor(TestContainer.class, "forAllIntAndString");
		Iterator<List<Shrinkable<Object>>> forAllGenerator = shrinkablesIterator(asList(1), asList(2));
		ResolveParameterHook stringInjector = parameterContext -> {
			assertThat(parameterContext.index()).isEqualTo(1);
			if (parameterContext.usage().isOfType(String.class)) {
				return Optional.of(() -> "aString");
			}
			return Optional.empty();
		};
		ResolvingParametersGenerator generator = new ResolvingParametersGenerator(
			propertyParameters,
			forAllGenerator,
			stringInjector
		);

		assertThat(generator.hasNext()).isTrue();
		assertThat(toValues(generator.next())).containsExactly(1, "aString");
		assertThat(generator.hasNext()).isTrue();
		assertThat(toValues(generator.next())).containsExactly(2, "aString");
		assertThat(generator.hasNext()).isFalse();
	}

	@Example
	void failIfParameterCannotBeResolved() {
		List<MethodParameter> propertyParameters = TestHelper.getParametersFor(TestContainer.class, "forAllIntAndString");
		Iterator<List<Shrinkable<Object>>> forAllGenerator = shrinkablesIterator(asList(1), asList(2));
		ResolvingParametersGenerator generator = new ResolvingParametersGenerator(
			propertyParameters,
			forAllGenerator,
			ResolveParameterHook.DO_NOT_RESOLVE
		);

		assertThat(generator.hasNext()).isTrue();
		assertThatThrownBy(() -> generator.next()).isInstanceOf(JqwikException.class);
	}

	@Example
	void resolveSeveralPositions() {
		List<MethodParameter> propertyParameters = TestHelper.getParametersFor(TestContainer.class, "stringIntStringInt");
		Iterator<List<Shrinkable<Object>>> forAllGenerator = shrinkablesIterator(asList(1, 2), asList(3, 4));
		ResolveParameterHook stringInjector = parameterContext -> {
			assertThat(parameterContext.index()).isIn(0, 2);
			if (parameterContext.usage().isOfType(String.class)) {
				return Optional.of(() -> "aString");
			}
			return Optional.empty();
		};
		ResolvingParametersGenerator generator = new ResolvingParametersGenerator(
			propertyParameters,
			forAllGenerator,
			stringInjector
		);

		assertThat(generator.hasNext()).isTrue();
		assertThat(toValues(generator.next())).containsExactly("aString", 1, "aString", 2);
		assertThat(generator.hasNext()).isTrue();
		assertThat(toValues(generator.next())).containsExactly("aString", 3, "aString", 4);
		assertThat(generator.hasNext()).isFalse();
	}

	@Property(tries = 10)
	@AddLifecycleHook(CreateAString.class)
	void resolverIsCalledOnce(@ForAll int ignore, String aString) {
		assertThat(aString).isEqualTo("aString");
		PropertyLifecycle.onSuccess(() -> assertThat(CreateAString.countInjectorCalls).isEqualTo(1));
	}

	@Property(tries = 10)
	@AddLifecycleHook(CreateAString.class)
	void supplierIsCalledOncePerTry(@ForAll int ignore, String aString) {
		assertThat(aString).isEqualTo("aString");
		PropertyLifecycle.onSuccess(() -> assertThat(CreateAString.countSupplierCalls).isEqualTo(10));
	}

	private List<Object> toValues(List<Shrinkable<Object>> shrinkables) {
		return shrinkables.stream().map(Shrinkable::value).collect(Collectors.toList());
	}

	@SafeVarargs
	private final Iterator<List<Shrinkable<Object>>> shrinkablesIterator(List<Object>... lists) {
		Iterator<List<Object>> valuesIterator = Arrays.stream(lists).iterator();

		return new Iterator<List<Shrinkable<Object>>>() {
			@Override
			public boolean hasNext() {
				return valuesIterator.hasNext();
			}

			@Override
			public List<Shrinkable<Object>> next() {
				List<Object> values = valuesIterator.next();
				return values.stream().map(Shrinkable::unshrinkable).collect(Collectors.toList());
			}
		};
	}

	private static class TestContainer {
		@Property
		void nothingToInject(@ForAll int anInt) {}

		@Property
		void forAllIntAndString(@ForAll int anInt, String aString) {}

		@Property
		void stringIntStringInt(String s1, @ForAll int i1, String s2, @ForAll int i2) {}
	}
}

class CreateAString implements ResolveParameterHook, AroundPropertyHook {

	static int countInjectorCalls = 0;
	static int countSupplierCalls = 0;

	@Override
	public Optional<Supplier<Object>> resolve(ParameterInjectionContext parameterContext) {
		countInjectorCalls++;
		if (parameterContext.usage().isOfType(String.class)) {
			return Optional.of(() -> {
				countSupplierCalls++;
				return "aString";
			});
		}
		return Optional.empty();
	}

	@Override
	public PropertyExecutionResult aroundProperty(PropertyLifecycleContext context, PropertyExecutor property) {
		countInjectorCalls = 0;
		countSupplierCalls = 0;
		return property.execute();
	}
}