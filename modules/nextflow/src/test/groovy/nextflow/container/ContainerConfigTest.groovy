/*
 * Copyright 2013-2023, Seqera Labs
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

package nextflow.container

import spock.lang.Specification
import spock.lang.Unroll

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerConfigTest extends Specification {

    @Unroll
    def 'should return env whitelist for=#VAL' () {
        when:
        def cfg = new ContainerConfig(envWhitelist: VAL)
        then:
        cfg.getEnvWhitelist() == EXPECTED

        where:
        VAL         | EXPECTED
        null        | []
        ''          | []
        'FOO'       | ['FOO']
        'FOO,BAR'   | ['FOO','BAR']
        'A ,, B,C ' | ['A','B','C']
        ['X','Y']   | ['X','Y']

    }

    def 'should validate legacy entry point' () {

        when:
        def cfg = new ContainerConfig(OPTS, ENV)
        then:
        cfg.entrypointOverride() == EXPECTED
        
        where:
        OPTS                            | ENV          | EXPECTED
        [:]                             | [:]          | false
        [entrypointOverride: false]     | [:]          | false
        [entrypointOverride: true]      | [:]          | true
        and:
        [:]                             | [NXF_CONTAINER_ENTRYPOINT_OVERRIDE: 'true']  | true
        [entrypointOverride: false]     | [NXF_CONTAINER_ENTRYPOINT_OVERRIDE: 'true']  | false

    }


    def 'should validate oci mode' () {

        when:
        def cfg = new ContainerConfig(OPTS)
        then:
        cfg.singularityOciMode() == EXPECTED

        where:
        OPTS                                | EXPECTED
        [:]                                 | false
        [oci:false]                         | false
        [oci:true]                          | false
        [engine:'apptainer', oci:true]      | false
        [engine:'docker', oci:true]         | false
        [engine:'singularity']              | false
        [engine:'singularity', oci:false]   | false
        [engine:'singularity', oci:true]    | true

    }

    def 'should get fusion options' () {
        when:
        def cfg = new ContainerConfig(OPTS)

        then:
        cfg.fusionOptions() == EXPECTED
        
        where:
        OPTS                                            | EXPECTED
        [:]                                             | null
        [engine:'docker']                               | '--rm --privileged'
        [engine:'podman']                               | '--rm --privileged'
        and:
        [engine:'docker', fusionOptions:'--cap-add foo']| '--cap-add foo'
        [engine:'podman', fusionOptions:'--cap-add bar']| '--cap-add bar'
        and:
        [engine:'sarus', fusionOptions:'--other']       | '--other'
    }

}
