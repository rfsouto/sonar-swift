package com.backelite.sonarqube.swift.complexity;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.measure.NewMeasure;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.batch.measure.Metric;
import org.sonar.api.config.Settings;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class LizardReportParserTest {
    private SensorContext context;
    private FileSystem fileSystem;
    private FilePredicates predicates;
    private InputFile inputFile;
    private NewMeasure<java.io.Serializable> newMeasure;

    @Before
    public void setUp() {
        context = mock(SensorContext.class);
        fileSystem = mock(FileSystem.class);
        predicates = mock(FilePredicates.class);
        inputFile = mock(InputFile.class);
        newMeasure = mock(NewMeasure.class);
        Settings settings = mock(Settings.class);
        when(context.settings()).thenReturn(settings);
        when(settings.getString(any())).thenReturn(null);

        when(context.fileSystem()).thenReturn(fileSystem);
        when(fileSystem.predicates()).thenReturn(predicates);
        FilePredicate fp = mock(FilePredicate.class);
        when(predicates.or(any(), any())).thenReturn(fp);
        when(fileSystem.hasFiles(fp)).thenReturn(true);
        when(fileSystem.inputFile(fp)).thenReturn(inputFile);
        when(context.newMeasure()).thenReturn(newMeasure);
        when(newMeasure.on(any(InputFile.class))).thenReturn(newMeasure);
        when(newMeasure.forMetric(any())).thenReturn(newMeasure);
        when(newMeasure.withValue(any(java.io.Serializable.class))).thenReturn(newMeasure);
    }

    @Test
    public void testParseFunctionItemWithAtFormat() throws Exception {
        // Simula un XML de Lizard con un item de función con formato 'name(...) at ruta/archivo.swift:linea'
        String xml = """
        <measure type=\"function\">
            <item name=\"example(...) at ./app/SonarTestApp/SonarTestAppTests/SonarTestAppTests.swift:13\">
                <value>1</value>
                <value>2</value>
                <value>1</value>
            </item>
        </measure>
        <measure type=\"file\">
            <item name=\"./app/SonarTestApp/SonarTestAppTests/SonarTestAppTests.swift\">
                <value>10</value>
                <value>20</value>
                <value>2</value>
            </item>
        </measure>
        """;
        Path tempFile = Files.createTempFile("lizard-report", ".xml");
        try (PrintWriter out = new PrintWriter(tempFile.toFile())) {
            out.print("<root>" + xml + "</root>");
        }

        LizardReportParser parser = new LizardReportParser(context);
        parser.parseReport(tempFile.toFile());

        // Verifica que se llama a newMeasure para el archivo correcto
        verify(context, atLeastOnce()).newMeasure();
        verify(newMeasure, atLeastOnce()).on(inputFile);
        verify(newMeasure, atLeastOnce()).forMetric(any());
        verify(newMeasure, atLeastOnce()).withValue(any(java.io.Serializable.class));
        verify(newMeasure, atLeastOnce()).save();
    }

    @Test
    public void testNoDuplicateFunctionComplexityMeasure() throws Exception {
        // Simula un XML de Lizard con dos funciones en el mismo archivo
        String xml = """
        <measure type=\"function\">
            <item name=\"func1() at ./app/SonarTestApp/SonarTestAppTests/SonarTestAppTests.swift:10\">
                <value>1</value>
                <value>2</value>
                <value>1</value>
            </item>
            <item name=\"func2() at ./app/SonarTestApp/SonarTestAppTests/SonarTestAppTests.swift:20\">
                <value>2</value>
                <value>3</value>
                <value>1</value>
            </item>
        </measure>
        <measure type=\"file\">
            <item name=\"./app/SonarTestApp/SonarTestAppTests/SonarTestAppTests.swift\">
                <value>10</value>
                <value>20</value>
                <value>2</value>
            </item>
        </measure>
        """;
        Path tempFile = Files.createTempFile("lizard-report", ".xml");
        try (PrintWriter out = new PrintWriter(tempFile.toFile())) {
            out.print("<root>" + xml + "</root>");
        }

        LizardReportParser parser = new LizardReportParser(context);
        parser.parseReport(tempFile.toFile());

        // Captura todas las métricas usadas en forMetric
        ArgumentCaptor<Metric> metricCaptor = ArgumentCaptor.forClass(Metric.class);
        verify(newMeasure, atLeastOnce()).forMetric(metricCaptor.capture());

        // Cuenta cuántas veces se usó COMPLEXITY
        long complexityCount = metricCaptor.getAllValues().stream()
            .filter(m -> m != null && m.key().toLowerCase().contains("complexity"))
            .count();

        // Debe ser solo una vez para la función (el archivo puede tener otra)
        assertTrue("No debe haber duplicados de COMPLEXITY por función", complexityCount <= 2);
    }

    @Test
    public void testNoDuplicateFileComplexityMeasure() throws Exception {
        // Simula un XML de Lizard con dos items de archivo para el mismo archivo
        String xml = """
        <measure type=\"file\">
            <item name=\"./app/SonarTestApp/SonarTestAppTests/SonarTestAppTests.swift\">
                <value>10</value>
                <value>20</value>
                <value>2</value>
            </item>
            <item name=\"./app/SonarTestApp/SonarTestAppTests/SonarTestAppTests.swift\">
                <value>15</value>
                <value>25</value>
                <value>3</value>
            </item>
        </measure>
        """;
        Path tempFile = Files.createTempFile("lizard-report", ".xml");
        try (PrintWriter out = new PrintWriter(tempFile.toFile())) {
            out.print("<root>" + xml + "</root>");
        }

        LizardReportParser parser = new LizardReportParser(context);
        parser.parseReport(tempFile.toFile());

        // Captura todas las métricas usadas en forMetric
        ArgumentCaptor<Metric> metricCaptor = ArgumentCaptor.forClass(Metric.class);
        verify(newMeasure, atLeastOnce()).forMetric(metricCaptor.capture());

        // Cuenta cuántas veces se usó COMPLEXITY
        long complexityCount = metricCaptor.getAllValues().stream()
            .filter(m -> m != null && m.key().toLowerCase().contains("complexity"))
            .count();

        // Debe ser solo una vez para el archivo
        assertEquals("No debe haber duplicados de COMPLEXITY por archivo", 1, complexityCount);
    }

    @Test
    public void testNoDuplicateAllFileMetrics() throws Exception {
        // Simula un XML de Lizard con dos items de archivo para el mismo archivo
        String xml = """
        <measure type=\"file\">
            <item name=\"./app/SonarTestApp/SonarTestAppTests/SonarTestAppTests.swift\">
                <value>10</value> <!-- COMPLEXITY -->
                <value>20</value> <!-- FUNCTIONS -->
                <value>2</value>  <!-- LINES -->
            </item>
            <item name=\"./app/SonarTestApp/SonarTestAppTests/SonarTestAppTests.swift\">
                <value>15</value>
                <value>25</value>
                <value>3</value>
            </item>
        </measure>
        """;
        Path tempFile = Files.createTempFile("lizard-report", ".xml");
        try (PrintWriter out = new PrintWriter(tempFile.toFile())) {
            out.print("<root>" + xml + "</root>");
        }

        LizardReportParser parser = new LizardReportParser(context);
        parser.parseReport(tempFile.toFile());

        // Captura todas las métricas usadas en forMetric
        ArgumentCaptor<Metric> metricCaptor = ArgumentCaptor.forClass(Metric.class);
        verify(newMeasure, atLeastOnce()).forMetric(metricCaptor.capture());

        // Cuenta cuántas veces se usó cada métrica
        long complexityCount = metricCaptor.getAllValues().stream()
            .filter(m -> m != null && m.key().toLowerCase().contains("complexity"))
            .count();
        long functionsCount = metricCaptor.getAllValues().stream()
            .filter(m -> m != null && m.key().toLowerCase().contains("functions"))
            .count();
        long linesCount = metricCaptor.getAllValues().stream()
            .filter(m -> m != null && m.key().toLowerCase().contains("lines"))
            .count();

        // Debe ser solo una vez cada métrica para el archivo
        assertEquals("No debe haber duplicados de COMPLEXITY por archivo", 1, complexityCount);
        assertEquals("No debe haber duplicados de FUNCTIONS por archivo", 1, functionsCount);
        assertEquals("No debe haber duplicados de LINES por archivo", 1, linesCount);
    }
}
