package com.github.kowalk.claudeapiswitch.services

import com.intellij.openapi.diagnostic.Logger

/**
 * Safely modifies the JVM [java.lang.ProcessEnvironment.theEnvironment] map
 * to inject or clear environment variables for child [ProcessBuilder] processes.
 *
 * JDK 21+ stores the map as `Map<Variable, Value>`, not `Map<String, String>`.
 * This class constructs the proper typed wrappers via reflection so the
 * internal map stays well-typed and doesn't crash other readers (e.g. the
 * terminal plugin).
 */
class ProcessEnvironmentPatcher(private val logger: Logger) {

    private data class ReflectionHandle(
        val theEnvironment: MutableMap<Any, Any>,
        val variableCtor: java.lang.reflect.Constructor<*>,
        val valueCtor: java.lang.reflect.Constructor<*>
    )

    private val handle: ReflectionHandle? by lazy {
        try {
            val envClass = Class.forName("java.lang.ProcessEnvironment")

            val theEnvField = envClass.getDeclaredField("theEnvironment")
            theEnvField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val theEnv = theEnvField.get(null) as MutableMap<Any, Any>

            // Variable(String) and Value(String) are package-private inner classes
            val varClass = Class.forName("java.lang.ProcessEnvironment\$Variable")
            val valClass = Class.forName("java.lang.ProcessEnvironment\$Value")
            val varCtor = varClass.getDeclaredConstructor(String::class.java).also { it.isAccessible = true }
            val valCtor = valClass.getDeclaredConstructor(String::class.java).also { it.isAccessible = true }

            ReflectionHandle(theEnv, varCtor, valCtor)
        } catch (e: Exception) {
            logger.warn("Cannot access ProcessEnvironment (${e.message})", e)
            null
        }
    }

    private fun makeVar(name: String): Any? =
        handle?.variableCtor?.newInstance(name)

    private fun makeValue(value: String): Any? =
        handle?.valueCtor?.newInstance(value)

    /** Sets environment variables in the JVM process environment. */
    fun setVars(vars: Map<String, String>) {
        val h = handle ?: return
        try {
            for ((k, v) in vars) {
                val key = makeVar(k) ?: continue
                val value = makeValue(v) ?: continue
                h.theEnvironment[key] = value
            }
            logger.info("Set ${vars.size} env vars in IDE process environment")
        } catch (e: Exception) {
            logger.warn("Could not set process env vars: ${e.message}")
        }
    }

    /** Removes environment variables from the JVM process environment. */
    fun clearVars(names: List<String>) {
        val h = handle ?: return
        try {
            for (name in names) {
                val key = makeVar(name) ?: continue
                h.theEnvironment.remove(key)
            }
            logger.info("Cleared ${names.size} env vars from IDE process environment")
        } catch (e: Exception) {
            logger.warn("Could not clear process env vars: ${e.message}")
        }
    }
}
