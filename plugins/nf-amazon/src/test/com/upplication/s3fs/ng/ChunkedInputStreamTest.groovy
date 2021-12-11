/*
 * Copyright 2020-2021, Seqera Labs
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
 *
 */

package com.upplication.s3fs.ng


import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ChunkedInputStreamTest extends Specification {

    def 'should read the chunks' () {
        given:
        def chunk1 = new String('Hello world\n').bytes
        def chunk2 = new String('Hola mundo\n').bytes
        def chunk3 = new String('Ciao mondo\n').bytes
        and:
        def len = chunk1.length+chunk2.length+chunk3.length
        def stream = new ChunkedInputStream(len)

        when:
        stream.add(chunk1);
        stream.add(chunk2);
        stream.add(chunk3);

        then:
        stream.text == '''\
            Hello world
            Hola mundo
            Ciao mondo
            '''.stripIndent()
    }

    def 'should read the chunks async' () {
        given:
        def chunk1 = new String('Hello world\n').bytes
        def chunk2 = new String('Hola mundo\n').bytes
        def chunk3 = new String('Ciao mondo\n').bytes
        and:
        def len = chunk1.length+chunk2.length+chunk3.length
        def stream = new ChunkedInputStream(len)

        when:
        Thread.start { sleep 100; stream.add(chunk1) }
        Thread.start { sleep 200; stream.add(chunk2) }
        Thread.start { sleep 300; stream.add(chunk3) }

        then:
        stream.text == '''\
            Hello world
            Hola mundo
            Ciao mondo
            '''.stripIndent()
    }

    def 'should read empty string' () {
        given:
        def stream = new ChunkedInputStream(0)
        expect:
        stream.text == ''
    }

    def 'should read throw an excpetion' () {
        given:
        def chunk1 = new String('Hello world\n').bytes
        def chunk2 = new String('Hola mundo\n').bytes
        def chunk3 = new String('Ciao mondo\n').bytes
        and:
        def len = chunk1.length+chunk2.length+chunk3.length
        def stream = new ChunkedInputStream(len)

        when:
        Thread.start { sleep 100; stream.add(chunk1) }
        Thread.start { sleep 200; stream.throwError(new IOException("Something break")) }
        and:
        println stream.text

        then:
        thrown(IOException)
    }

}
