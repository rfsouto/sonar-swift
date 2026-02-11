package com.backelite.sonarqube.swift.issues.swiftlint;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rule.RuleKey;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class SwiftLintReportParserTest {
    private SensorContext context;
    private FileSystem fileSystem;
    private FilePredicates predicates;
    private InputFile inputFile;
    private NewIssue newIssue;
    private NewIssueLocation newIssueLocation;

    @Before
    public void setUp() {
        context = Mockito.mock(SensorContext.class);
        fileSystem = Mockito.mock(FileSystem.class);
        predicates = Mockito.mock(FilePredicates.class);
        inputFile = Mockito.mock(InputFile.class);
        newIssue = Mockito.mock(NewIssue.class);
        newIssueLocation = Mockito.mock(NewIssueLocation.class);

        Mockito.when(context.fileSystem()).thenReturn(fileSystem);
        Mockito.when(fileSystem.predicates()).thenReturn(predicates);
        FilePredicate fp = Mockito.mock(FilePredicate.class);
        Mockito.when(predicates.or(Mockito.any(), Mockito.any())).thenReturn(fp);
        Mockito.when(fileSystem.hasFiles(fp)).thenReturn(true);
        Mockito.when(fileSystem.inputFile(fp)).thenReturn(inputFile);
        Mockito.when(inputFile.selectLine(Mockito.anyInt())).thenReturn(null);
        Mockito.when(context.newIssue()).thenReturn(newIssue);
        Mockito.when(newIssue.newLocation()).thenReturn(newIssueLocation);
        Mockito.when(newIssueLocation.on(inputFile)).thenReturn(newIssueLocation);
        Mockito.when(newIssueLocation.at(null)).thenReturn(newIssueLocation);
        Mockito.when(newIssueLocation.message(Mockito.anyString())).thenReturn(newIssueLocation);
        Mockito.when(newIssue.forRule(Mockito.any())).thenReturn(newIssue);
        Mockito.when(newIssue.at(newIssueLocation)).thenReturn(newIssue);
        Mockito.doNothing().when(newIssue).save();
    }

    @Test
    public void testParseReport() throws Exception {
        // Crear un informe SwiftLint simulado en formato JSON
        String report = "[\n" +
                "  {\n" +
                "    \"character\" : 5,\n" +
                "    \"file\" : \"TestFile.swift\",\n" +
                "    \"line\" : 12,\n" +
                "    \"reason\" : \"Prefer `static` over `class` in a final class\",\n" +
                "    \"rule_id\" : \"static_over_final_class\",\n" +
                "    \"severity\" : \"Warning\",\n" +
                "    \"type\" : \"Static Over Final Class\"\n" +
                "  }\n" +
                "]";
        Path tempFile = Files.createTempFile("swiftlint-report", ".json");
        try (PrintWriter out = new PrintWriter(tempFile.toFile())) {
            out.print(report);
        }

        SwiftLintReportParser parser = new SwiftLintReportParser(context);
        parser.parseReport(tempFile.toFile());

        // Verifica que se reporta la incidencia
        Mockito.verify(newIssue).forRule(RuleKey.of(SwiftLintRulesDefinition.REPOSITORY_KEY, "static_over_final_class"));
        Mockito.verify(newIssue).at(newIssueLocation);
        Mockito.verify(newIssue).save();
    }
}
