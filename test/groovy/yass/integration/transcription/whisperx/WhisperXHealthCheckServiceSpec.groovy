package yass.integration.transcription.whisperx

import spock.lang.Specification
import spock.lang.Unroll
import yass.YassProperties

class WhisperXHealthCheckServiceSpec extends Specification {

    @Unroll
    def "recommendRuntime returns stable recommendation for cuda=#cuda vram=#vram mps=#mps"() {
        when:
        def rec = WhisperXHealthCheckService.recommendRuntime(cuda, vram, mps)

        then:
        rec.device == expectedDevice
        rec.computeType == expectedCompute
        rec.model == expectedModel

        where:
        cuda  | vram  | mps   || expectedDevice | expectedCompute | expectedModel
        true  | 12288 | false || "cuda"         | "float16"       | "large-v3"
        true  | 9000  | false || "cuda"         | "float16"       | "medium"
        true  | 7000  | false || "cuda"         | "float16"       | "small"
        true  | 5000  | false || "cpu"          | "int8"          | "small"
        true  | null  | false || "cpu"          | "int8"          | "small"
        false | null  | true  || "mps"          | "float32"       | "medium"
        false | null  | false || "cpu"          | "int8"          | "small"
    }

    def "transcription service resolves effective values when model/device/compute are auto"() {
        given:
        def properties = new YassProperties()
        properties.setProperty("whisperx-model", "auto")
        properties.setProperty("whisperx-effective-model", "medium")
        properties.setProperty("whisperx-device", "auto")
        properties.setProperty("whisperx-effective-device", "cuda")
        properties.setProperty("whisperx-compute-type", "auto")
        properties.setProperty("whisperx-effective-compute-type", "float16")
        def service = new WhisperXTranscriptionService(properties)

        expect:
        invokePrivate(service, "resolveModel") == "medium"
        invokePrivate(service, "resolveDevice") == "cuda"
        invokePrivate(service, "resolveComputeType") == "float16"
    }

    def "transcription service keeps manual values even when effective auto values exist"() {
        given:
        def properties = new YassProperties()
        properties.setProperty("whisperx-model", "large-v3")
        properties.setProperty("whisperx-effective-model", "small")
        properties.setProperty("whisperx-device", "cpu")
        properties.setProperty("whisperx-effective-device", "cuda")
        properties.setProperty("whisperx-compute-type", "int8")
        properties.setProperty("whisperx-effective-compute-type", "float16")
        def service = new WhisperXTranscriptionService(properties)

        expect:
        invokePrivate(service, "resolveModel") == "large-v3"
        invokePrivate(service, "resolveDevice") == "cpu"
        invokePrivate(service, "resolveComputeType") == "int8"
    }

    def "detectPythonExecutable resolves an absolute interpreter path"() {
        when:
        def detected = new WhisperXHealthCheckService("", true, "whisperx").detectPythonExecutable()

        then:
        detected
        new File(detected).isAbsolute()
        new File(detected).exists()
    }

    def "managed python executable points into dedicated whisperx venv"() {
        expect:
        WhisperXHealthCheckService.getManagedVenvDirectory().toString().contains(".yass")
        WhisperXHealthCheckService.getManagedVenvDirectory().toString().contains("whisperx-venv")
        WhisperXHealthCheckService.getManagedPythonExecutable().toString().contains("whisperx-venv")
    }

    private static Object invokePrivate(Object instance, String methodName) {
        def method = instance.class.getDeclaredMethod(methodName)
        method.setAccessible(true)
        method.invoke(instance)
    }
}
