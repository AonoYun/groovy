/*
 * Copyright 2003-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.transform

import gls.CompilableTestSupport

/**
 * @author Alex Tkachman
 * @author Guillaume Laforge
 * @author Paul King
 */
class DelegateTransformTest extends CompilableTestSupport {

    /** fix for GROOVY-3380   */
    void testDelegateImplementingANonPublicInterface() {
        assertScript """
            import org.codehaus.groovy.transform.ClassImplementingANonPublicInterface

            class DelegatingToClassImplementingANonPublicInterface {
                @Delegate ClassImplementingANonPublicInterface delegate = new ClassImplementingANonPublicInterface()
            }

            def constant = new DelegatingToClassImplementingANonPublicInterface().returnConstant()
            assert constant == "constant"
        """
    }

    /** fix for GROOVY-3380   */
    void testDelegateImplementingANonPublicInterfaceWithZipFileConcreteCase() {
        assertScript """
            import java.util.zip.*

            class ZipWrapper{
               @Delegate ZipFile zipFile
            }

            new ZipWrapper()
        """
    }

    void testLock() {
        def res = new GroovyShell().evaluate("""
              import java.util.concurrent.locks.*

              class LockableMap {
                 @Delegate private Map map = [:]

                 @Delegate private Lock lock = new ReentrantLock ()

                 @Delegate(interfaces=false) private List list = new ArrayList ()
              }

              new LockableMap ()
        """)

        res.lock()
        try {
            res[0] = 0
            res[1] = 1
            res[2] = 2

            res.add("in list")
        }
        finally {
            res.unlock()
        }

        assertEquals([0: 0, 1: 1, 2: 2], res.@map)
        assertEquals("in list", res.@list[0])

        assertTrue res instanceof Map
        assertTrue res instanceof java.util.concurrent.locks.Lock
        assertFalse res instanceof List
    }

    void testMultiple() {
        def res = new GroovyShell().evaluate("""
        class X {
          def value = 10
        }

        class Y {
          @Delegate X  x  = new X ()
          @Delegate XX xx = new XX ()

          void setValue (v) {
            this.@x.@value = 12
          }
        }

        class XX {
          def value2 = 11
        }

        new Y ()
        """)

        assertEquals 10, res.value
        assertEquals 11, res.value2
        res.value = 123
        assertEquals 12, res.value
    }

    void testUsingDateCompiles() {
        assertScript """
        class Foo { 
          @Delegate Date d = new Date(); 
        } 
        Foo
      """
    }

    /** fix for GROOVY-3471   */
    void testDelegateOnAMapTypeFieldWithInitializationUsingConstructorProperties() {
        assertScript """
            class Test3471 { @Delegate Map mp }
            def t = new Test3471(mp: new HashMap()) // this was resulting in a NPE due to MetaClassImpl's special handling of Map
            assert t.keySet().size() == 0
        """
    }

    /** GROOVY-3323   */
    void testDelegateTransformCorrectlyDelegatesMethodsFromSuperInterfaces() {
        assert new DelegateBarImpl(new DelegateFooImpl()).bar() == 'bar impl'
        assert new DelegateBarImpl(new DelegateFooImpl()).foo() == 'foo impl'
    }

    /** GROOVY-3555   */
    void testDelegateTransformIgnoresDeprecatedMethodsByDefault() {
        def b1 = new DelegateBarForcingDeprecated(baz: new BazWithDeprecatedFoo())
        def b2 = new DelegateBarWithoutDeprecated(baz: new BazWithDeprecatedFoo())
        assert b1.bar() == 'bar'
        assert b2.bar() == 'bar'
        assert b1.foo() == 'foo'
        shouldFail(MissingMethodException) {
            assert b2.foo() == 'foo'
        }
    }

    /** GROOVY-4163   */
    void testDelegateTransformAllowsInterfacesAndDelegation() {
        assertScript """
            class Temp implements Runnable {
                @Delegate
                private Thread runnable

                static main(args) {
                    def thread = Thread.currentThread()
                    def temp = new Temp(runnable: thread)
                }
            }
        """
    }

    void testDelegateToSelfTypeShouldFail() {
        shouldNotCompile """
            class B {
                @Delegate B b = new B()
                static main(args){
                    new B()
                }
            }
        """
    }

    // GROOVY-4265
    void testShouldPreferDelegatedOverStaticSuperMethod() {
        assertScript """
            class A {
                static foo(){"A->foo()"}
            }
            class B extends A {
                @Delegate C c = new C()
            }
            class C {
                def foo(){"C->foo()"}
            }
            assert new B().foo() == 'C->foo()'
        """
    }

    void testDelegateToObjectShouldFail() {
        shouldNotCompile """
            class B {
                @Delegate b = new Object()
            }
        """
    }

    /** GROOVY-4244 */
    void testSetPropertiesThroughDelegate() {
        def foo = new Foo4244()

        assert foo.nonFinalBaz == 'Initial value - nonFinalBaz'
        foo.nonFinalBaz = 'New value - nonFinalBaz'
        assert foo.nonFinalBaz == 'New value - nonFinalBaz'

        assert foo.finalBaz == 'Initial value - finalBaz'
        shouldFail(ReadOnlyPropertyException) {
            foo.finalBaz = 'New value - finalBaz'
        }
    }

    void testDelegateSuperInterfaces_Groovy4619() {
        assert 'doSomething' in SomeClass4619.class.methods*.name
    }

    // GROOVY-5112
    void testGenericsOnArray() {
        assertScript '''
            class ListWrapper {
              @Delegate
              List myList

              @Delegate
              URL homepage
            }
            new ListWrapper()
        '''
    }
}

interface DelegateFoo {
    def foo()
}

class DelegateFooImpl implements DelegateFoo {
    def foo() { 'foo impl' }
}

interface DelegateBar extends DelegateFoo {
    def bar()
}

class DelegateBarImpl implements DelegateBar {
    @Delegate DelegateFoo foo;

    DelegateBarImpl(DelegateFoo f) { this.foo = f}

    def bar() { 'bar impl'}
}

class BazWithDeprecatedFoo {
    @Deprecated foo() { 'foo' }
    def bar() { 'bar' }
}

class DelegateBarWithoutDeprecated {
    @Delegate BazWithDeprecatedFoo baz
}

class DelegateBarForcingDeprecated {
    @Delegate(deprecated=true) BazWithDeprecatedFoo baz
}

class Foo4244 {
    @Delegate Bar4244 bar = new Bar4244()
}

class Bar4244 {
    String nonFinalBaz = "Initial value - nonFinalBaz"
    final String finalBaz = "Initial value - finalBaz"
}

interface SomeInterface4619 {
    void doSomething()
}

interface SomeOtherInterface4619 extends SomeInterface4619 {}

class SomeClass4619 {
    @Delegate
    SomeOtherInterface4619 delegate
}
