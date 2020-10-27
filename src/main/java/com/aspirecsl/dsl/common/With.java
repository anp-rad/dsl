package com.aspirecsl.dsl.common;

/**
 * A DSL decorator interface that provides the method <tt>with()</tt> to improve readability in code.
 * <p>
 * For example if <tt>FooBuilder</tt> builds <tt>Foo</tt> with a property <tt>bar</tt> then the <tt>With</tt>
 * implementation that returns a <tt>FooBuilder</tt> instance can improve the DSL readability as below:
 * <pre><code>
 *     /-- DSL class --/
 *     public class DSL{
 *         public static{@literal With<FooBuilder>} fooBuilder(){
 *             return (){@literal ->} new FooBuilder();
 *         }
 *     }
 *
 *     /-- DSL Usage --/
 *     DSL.fooBuilder()
 *        .with()
 *        .bar(valueOfBar)
 *        .build()
 * </code></pre>
 * <p>
 *
 * @param <T> the type of the object returned by the <tt>with()</tt> method
 * @author anoopr
 * @version 19.1
 * @since 19.1
 */
public interface With<T> {
    /**
     * Returns the object wrapped by this decorator
     *
     * @return the object wrapped by this decorator
     */
    T with();
}
