package com.schwarzit.spectralIntellijPlugin;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ScriptRunnerUtil;
import com.schwarzit.spectralIntellijPlugin.exceptions.SpectralException;
import com.schwarzit.spectralIntellijPlugin.exceptions.TempFileException;
import com.schwarzit.spectralIntellijPlugin.models.ErrorPosition;
import com.schwarzit.spectralIntellijPlugin.models.ErrorRange;
import com.schwarzit.spectralIntellijPlugin.models.SpectralIssue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpectralRunnerTest {
    private SpectralRunner spectralRunner;

    private final String spectralOutput = "[\n" +
            "\t{\n" +
            "\t\t\"code\": \"description-is-mandatory\",\n" +
            "\t\t\"path\": [\n" +
            "\t\t\t\"paths\",\n" +
            "\t\t\t\"/api-linting/api/v1/rules\"\n" +
            "\t\t],\n" +
            "\t\t\"message\": \"Every route of an API should have a description; property: /api-linting/api/v1/rules.description is missing\",\n" +
            "\t\t\"severity\": 1,\n" +
            "\t\t\"range\": {\n" +
            "\t\t\t\"start\": {\n" +
            "\t\t\t\t\"line\": 3,\n" +
            "\t\t\t\t\"character\": 36\n" +
            "\t\t\t},\n" +
            "\t\t\t\"end\": {\n" +
            "\t\t\t\t\"line\": 41,\n" +
            "\t\t\t\t\"character\": 27\n" +
            "\t\t\t}\n" +
            "\t\t},\n" +
            "\t\t\"source\": \"/openapi/oas_3.json\"\n" +
            "\t}" +
            "]";

    @BeforeEach
    void setUp() {
        StorageManager mockStorageManager = mock(StorageManager.class);

        File mockFile = mock(File.class);
        when(mockFile.getAbsolutePath()).thenReturn("/");
        when(mockStorageManager.getSpectralExecutable()).thenReturn(mockFile);
        when(mockStorageManager.getStoragePath()).thenReturn(Path.of("/storage"));

        spectralRunner = new SpectralRunner(mockStorageManager);
    }

    @Test
    void testLint() throws SpectralException, TempFileException {
        try (MockedStatic<ScriptRunnerUtil> scriptRunnerUtilMock = Mockito.mockStatic(ScriptRunnerUtil.class)) {
            scriptRunnerUtilMock.when(() -> ScriptRunnerUtil.getProcessOutput(isA(GeneralCommandLine.class))).thenReturn(spectralOutput);

            List<SpectralIssue> issues = spectralRunner.lint("FileContent", "RulesetPath");

            Assertions.assertEquals(1, issues.size());
            SpectralIssue issue = issues.get(0);

            Assertions.assertEquals("description-is-mandatory", issue.getCode());
            Assertions.assertArrayEquals(new String[]{"paths", "/api-linting/api/v1/rules"}, issue.getPath());
            Assertions.assertEquals("Every route of an API should have a description; property: /api-linting/api/v1/rules.description is missing", issue.getMessage());
            Assertions.assertEquals(1, issue.getSeverity());
            Assertions.assertEquals(new ErrorRange(new ErrorPosition(3, 36), new ErrorPosition(41, 27)), issue.getRange());
            Assertions.assertEquals("/openapi/oas_3.json", issue.getSource());
        }
    }

    @Test
    void testLintWithExecutionError() {
        try (MockedStatic<ScriptRunnerUtil> scriptRunnerUtilMock = Mockito.mockStatic(ScriptRunnerUtil.class)) {
            scriptRunnerUtilMock.when(() -> ScriptRunnerUtil.getProcessOutput(isA(GeneralCommandLine.class))).thenThrow(new ExecutionException("Execution failed"));
            SpectralException spectralException = Assertions.assertThrows(SpectralException.class, () -> spectralRunner.lint("FileContent", "RulesetPath"));
            Assertions.assertTrue(spectralException.getMessage().contains("Execution of Spectral failed"));
        }
    }

    @Test
    void testLintWithSubstringError() {
        try (MockedStatic<ScriptRunnerUtil> scriptRunnerUtilMock = Mockito.mockStatic(ScriptRunnerUtil.class)) {
            scriptRunnerUtilMock.when(() -> ScriptRunnerUtil.getProcessOutput(isA(GeneralCommandLine.class))).thenReturn(spectralOutput.substring(0, spectralOutput.lastIndexOf(']')));

            SpectralException spectralException = Assertions.assertThrows(SpectralException.class, () -> spectralRunner.lint("FileContent", "RulesetPath"));
            Assertions.assertTrue(spectralException.getMessage().contains("Execution of Spectral failed"));
        }
    }

    @Test
    void testLintWithParsingError() {
        try (MockedStatic<ScriptRunnerUtil> scriptRunnerUtilMock = Mockito.mockStatic(ScriptRunnerUtil.class)) {
            scriptRunnerUtilMock.when(() -> ScriptRunnerUtil.getProcessOutput(isA(GeneralCommandLine.class))).thenReturn(spectralOutput.replace("\"", ""));

            SpectralException spectralException = Assertions.assertThrows(SpectralException.class, () -> spectralRunner.lint("FileContent", "RulesetPath"));
            Assertions.assertTrue(spectralException.getMessage().contains("Execution of Spectral failed"));
        }
    }
}