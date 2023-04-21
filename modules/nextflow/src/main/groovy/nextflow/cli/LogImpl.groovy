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

package nextflow.cli

import java.nio.file.Path

import ch.artecat.grengine.Grengine
import com.google.common.hash.HashCode
import groovy.text.Template
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.exception.AbortOperationException
import nextflow.file.FileHelper
import nextflow.plugin.Plugins
import nextflow.processor.TaskRun
import nextflow.processor.TaskTemplateEngine
import nextflow.trace.TraceRecord
import nextflow.ui.TableBuilder

import static nextflow.cli.CmdHelper.fixEqualsOp

/**
 * CLI `log` sub-command
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class LogImpl implements CacheBase {

    static private List<String> ALL_FIELDS

    static private DEFAULT_FIELDS = 'workdir'

    static {
        ALL_FIELDS = []
        ALL_FIELDS.addAll( TraceRecord.FIELDS.keySet().collect { it.startsWith('%') ? 'p'+it.substring(1) : it } )
        ALL_FIELDS << 'stdout'
        ALL_FIELDS << 'stderr'
        ALL_FIELDS << 'log'
        ALL_FIELDS.sort(true)
    }

    interface Options {
        String getAfter()
        String getBefore()
        String getBut()
        String getFields()
        String getFilterStr()
        boolean getListFields()
        boolean getQuiet()
        String getSeparator()
        String getTemplateStr()
        List<String> getArgs()
    }

    @Delegate
    private Options options

    private Script filterScript

    private boolean showHistory

    private Template templateScript

    private Map<HashCode,Boolean> printed = new HashMap<>()

    LogImpl(Options options) {
        this.options = options
    }

    /* For testing purposes only */
    LogImpl() {}

    void run() {
        Plugins.init()
        init()

        // -- show the list of expected fields and exit
        if( listFields ) {
            ALL_FIELDS.each { println "  $it" }
            return
        }

        // -- show the current history and exit
        if( showHistory ) {
            quiet ? printQuiet() : printHistory()
            return
        }

        // -- main
        listIds().each { entry ->
            cacheFor(entry)
                .openForRead()
                .eachRecord(this.&printRecord)
                .close()
        }

    }

    void init() {
        CacheBase.super.init()

        //
        // validate input options
        //
        if( fields && templateStr )
            throw new AbortOperationException("Options `-f` and `-t` cannot be used in the same command")

        //
        // when no CLI options have been specified, just show the history log
        showHistory = !args && !before && !after && !but

        //
        // initialise filter engine
        //
        if( filterStr ) {
            filterScript = new Grengine().create("{ it -> ${fixEqualsOp(filterStr)} }")
        }

        //
        // initialize the template engine
        //
        def templateStr1
        if( !templateStr ) {
            String fields1 = fields ?: DEFAULT_FIELDS
            templateStr1 = fields1
                .tokenize(',  \n')
                .collect { '$'+it }
                .join(separator)
        }
        else if( new File(templateStr).exists() ) {
            templateStr1 = new File(templateStr).text
        }

        templateScript = new TaskTemplateEngine().createTemplate(templateStr1)
    }

    /**
     * Print a log {@link TraceRecord} the the standard output by using the specified {@link #templateStr}
     *
     * @param record A {@link TraceRecord} instance representing a task runtime information
     */
    protected void printRecord(HashCode hash, TraceRecord record) {

        if( printed.containsKey(hash) )
            return
        else
            printed.put(hash,Boolean.TRUE)

        final adaptor = new TraceAdaptor(record)

        if( filterScript ) {
            filterScript.setBinding(adaptor)
            // dynamic execution of the filter statement
            // the `run` method interprets the statement groovy closure
            // then the `call` method invokes the closure which returns a bool value
            // if `false` skip this record
            if( !((Closure)filterScript.run()).call() ) {
                return
            }
        }

        println templateScript.make(adaptor).toString()
    }

    private void printHistory() {
        def table = new TableBuilder(cellSeparator: '\t')
            .head('TIMESTAMP')
            .head('DURATION')
            .head('RUN NAME')
            .head('STATUS')
            .head('REVISION ID')
            .head('SESSION ID')
            .head('COMMAND')

        history.eachRow { List<String> row ->
            row[4] = row[4].size()>10 ? row[4].substring(0,10) : row[4]
            table.append(row)
        }

        println table.toString()
    }

    private void printQuiet() {
        history.eachRow { List row -> println(row[2]) }
    }

    /**
     * Wrap a {@link TraceRecord} instance as a {@link Map} or a {@link Binding} object
     */
    private static class TraceAdaptor extends Binding {

        static private int MAX_LINES = 100

        private TraceRecord record

        @Delegate
        private Map<String,Object> delegate = [:]

        TraceAdaptor(TraceRecord record) {
            this.record = record
        }

        @Override
        boolean containsKey(Object key) {
            delegate.containsKey(key.toString()) || record.containsKey(key.toString())
        }

        @Override
        Object get(Object key) {
            if( delegate.containsKey(key) ) {
                return delegate.get(key)
            }

            if( key == 'stdout' ) {
                return fetch(getWorkDir().resolve(TaskRun.CMD_OUTFILE))
            }

            if( key == 'stderr' ) {
                return fetch(getWorkDir().resolve(TaskRun.CMD_ERRFILE))
            }

            if( key == 'log' ) {
                return fetch(getWorkDir().resolve(TaskRun.CMD_LOG))
            }

            if( key == 'pcpu' )
                return record.getFmtStr('%cpu')

            if( key == 'pmem' )
                return record.getFmtStr('%mem')

            return record.getFmtStr(normaliseKey(key))
        }


        String normaliseKey(key) {
            key .toString() .toLowerCase()
        }

        Object getVariable(String name) {

            if( name == 'pcpu' )
                return record.store.get('%cpu')

            if( name == 'pmem' )
                return record.store.get('%mem')

            if( record.containsKey(name) )
                return record.store.get(name)

            throw new MissingPropertyException(name)
        }

        Map getVariables() {
            new HashMap(record.store)
        }

        private Path getWorkDir() {
            def folder = (String)record.get('workdir')
            folder ? FileHelper.asPath(folder) : null
        }

        private String fetch(Path path) {
            try {
                int c=0
                def result = new StringBuilder()
                path.withReader { reader ->
                    String line
                    while( (line=reader.readLine()) && c++<MAX_LINES ) {
                        result << line << '\n'
                    }
                }

                result.toString() ?: TraceRecord.NA

            }
            catch( IOError e ) {
                log.debug "Failed to fetch content for file: $path -- Cause: ${e.message ?: e}"
                return TraceRecord.NA
            }
        }
    }
}