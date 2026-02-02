/*
 * Copyright (C) 2016-2025 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.aem.onstartup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import com.viglet.dumont.connector.plugin.aem.export.DumAemExchangeProcess;
import com.viglet.dumont.connector.plugin.aem.persistence.model.DumAemConfigVar;
import com.viglet.dumont.connector.plugin.aem.persistence.repository.DumAemConfigVarRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("DumAemOnStartupJpa Tests")
class DumAemOnStartupJpaTest {

    @Mock
    private DumAemConfigVarRepository dumAemConfigVarRepository;

    @Mock
    private DumAemExchangeProcess dumAemExchangeProcess;

    @Mock
    private ApplicationArguments applicationArguments;

    private DumAemOnStartupJpa dumAemOnStartupJpa;

    @BeforeEach
    void setUp() {
        dumAemOnStartupJpa = new DumAemOnStartupJpa(
                dumAemConfigVarRepository,
                dumAemExchangeProcess);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create instance with dependencies")
        void shouldCreateInstanceWithDependencies() {
            assertNotNull(dumAemOnStartupJpa);
        }
    }

    @Nested
    @DisplayName("FIRST_TIME Constant Tests")
    class FirstTimeConstantTests {

        @Test
        @DisplayName("Should have FIRST_TIME constant value")
        void shouldHaveFirstTimeConstantValue() {
            assertEquals("FIRST_TIME", DumAemOnStartupJpa.FIRST_TIME);
        }
    }

    @Nested
    @DisplayName("run Tests")
    class RunTests {

        @Test
        @DisplayName("Should skip configuration when FIRST_TIME already exists")
        void shouldSkipConfigurationWhenFirstTimeAlreadyExists() {
            DumAemConfigVar existingConfig = new DumAemConfigVar();
            existingConfig.setId(DumAemOnStartupJpa.FIRST_TIME);

            when(dumAemConfigVarRepository.findById(DumAemOnStartupJpa.FIRST_TIME))
                    .thenReturn(Optional.of(existingConfig));

            dumAemOnStartupJpa.run(applicationArguments);

            verify(dumAemConfigVarRepository).findById(DumAemOnStartupJpa.FIRST_TIME);
            verify(dumAemConfigVarRepository, never()).save(any(DumAemConfigVar.class));
        }

        @Test
        @DisplayName("Should run first time configuration when FIRST_TIME does not exist")
        void shouldRunFirstTimeConfigurationWhenFirstTimeDoesNotExist() {
            when(dumAemConfigVarRepository.findById(DumAemOnStartupJpa.FIRST_TIME))
                    .thenReturn(Optional.empty());
            when(dumAemConfigVarRepository.save(any(DumAemConfigVar.class)))
                    .thenReturn(new DumAemConfigVar());

            dumAemOnStartupJpa.run(applicationArguments);

            verify(dumAemConfigVarRepository).findById(DumAemOnStartupJpa.FIRST_TIME);
            verify(dumAemConfigVarRepository).save(any(DumAemConfigVar.class));
        }
    }

    @Nested
    @DisplayName("setFirstTime Tests")
    class SetFirstTimeTests {

        @Test
        @DisplayName("Should save FIRST_TIME config var")
        void shouldSaveFirstTimeConfigVar() {
            when(dumAemConfigVarRepository.findById(DumAemOnStartupJpa.FIRST_TIME))
                    .thenReturn(Optional.empty());
            when(dumAemConfigVarRepository.save(any(DumAemConfigVar.class)))
                    .thenReturn(new DumAemConfigVar());

            dumAemOnStartupJpa.run(applicationArguments);

            ArgumentCaptor<DumAemConfigVar> captor = ArgumentCaptor.forClass(DumAemConfigVar.class);
            verify(dumAemConfigVarRepository).save(captor.capture());

            DumAemConfigVar savedConfig = captor.getValue();
            assertEquals(DumAemOnStartupJpa.FIRST_TIME, savedConfig.getId());
            assertEquals("/system", savedConfig.getPath());
            assertEquals("true", savedConfig.getValue());
        }
    }

    @Nested
    @DisplayName("Export File Processing Tests")
    class ExportFileProcessingTests {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Should process JSON files from export directory")
        void shouldProcessJsonFilesFromExportDirectory() throws IOException {
            // Create a temporary export directory and JSON file
            Path exportDir = tempDir.resolve("export");
            Files.createDirectories(exportDir);
            Path jsonFile = exportDir.resolve("test-export.json");
            Files.writeString(jsonFile, "{}");

            // Set the user.dir to temp directory
            String originalUserDir = System.getProperty("user.dir");
            try {
                System.setProperty("user.dir", tempDir.toString());

                when(dumAemConfigVarRepository.findById(DumAemOnStartupJpa.FIRST_TIME))
                        .thenReturn(Optional.empty());
                when(dumAemConfigVarRepository.save(any(DumAemConfigVar.class)))
                        .thenReturn(new DumAemConfigVar());
                doNothing().when(dumAemExchangeProcess).importFromFile(any(File.class));

                dumAemOnStartupJpa.run(applicationArguments);

                verify(dumAemExchangeProcess, times(1)).importFromFile(any(File.class));
            } finally {
                System.setProperty("user.dir", originalUserDir);
            }
        }

        @Test
        @DisplayName("Should skip non-existent files")
        void shouldSkipNonExistentFiles() {
            when(dumAemConfigVarRepository.findById(DumAemOnStartupJpa.FIRST_TIME))
                    .thenReturn(Optional.empty());
            when(dumAemConfigVarRepository.save(any(DumAemConfigVar.class)))
                    .thenReturn(new DumAemConfigVar());

            dumAemOnStartupJpa.run(applicationArguments);

            verify(dumAemExchangeProcess, never()).importFromFile(any(File.class));
        }

        @Test
        @DisplayName("Should handle empty export directory")
        void shouldHandleEmptyExportDirectory() throws IOException {
            Path exportDir = tempDir.resolve("export");
            Files.createDirectories(exportDir);

            String originalUserDir = System.getProperty("user.dir");
            try {
                System.setProperty("user.dir", tempDir.toString());

                when(dumAemConfigVarRepository.findById(DumAemOnStartupJpa.FIRST_TIME))
                        .thenReturn(Optional.empty());
                when(dumAemConfigVarRepository.save(any(DumAemConfigVar.class)))
                        .thenReturn(new DumAemConfigVar());

                dumAemOnStartupJpa.run(applicationArguments);

                verify(dumAemExchangeProcess, never()).importFromFile(any(File.class));
            } finally {
                System.setProperty("user.dir", originalUserDir);
            }
        }

        @Test
        @DisplayName("Should process multiple JSON files")
        void shouldProcessMultipleJsonFiles() throws IOException {
            Path exportDir = tempDir.resolve("export");
            Files.createDirectories(exportDir);
            Files.writeString(exportDir.resolve("export1.json"), "{}");
            Files.writeString(exportDir.resolve("export2.json"), "{}");
            Files.writeString(exportDir.resolve("export3.json"), "{}");

            String originalUserDir = System.getProperty("user.dir");
            try {
                System.setProperty("user.dir", tempDir.toString());

                when(dumAemConfigVarRepository.findById(DumAemOnStartupJpa.FIRST_TIME))
                        .thenReturn(Optional.empty());
                when(dumAemConfigVarRepository.save(any(DumAemConfigVar.class)))
                        .thenReturn(new DumAemConfigVar());
                doNothing().when(dumAemExchangeProcess).importFromFile(any(File.class));

                dumAemOnStartupJpa.run(applicationArguments);

                verify(dumAemExchangeProcess, times(3)).importFromFile(any(File.class));
            } finally {
                System.setProperty("user.dir", originalUserDir);
            }
        }

        @Test
        @DisplayName("Should skip non-JSON files")
        void shouldSkipNonJsonFiles() throws IOException {
            Path exportDir = tempDir.resolve("export");
            Files.createDirectories(exportDir);
            Files.writeString(exportDir.resolve("test.txt"), "content");
            Files.writeString(exportDir.resolve("test.xml"), "<xml/>");
            Files.writeString(exportDir.resolve("test.json"), "{}");

            String originalUserDir = System.getProperty("user.dir");
            try {
                System.setProperty("user.dir", tempDir.toString());

                when(dumAemConfigVarRepository.findById(DumAemOnStartupJpa.FIRST_TIME))
                        .thenReturn(Optional.empty());
                when(dumAemConfigVarRepository.save(any(DumAemConfigVar.class)))
                        .thenReturn(new DumAemConfigVar());
                doNothing().when(dumAemExchangeProcess).importFromFile(any(File.class));

                dumAemOnStartupJpa.run(applicationArguments);

                // Only the JSON file should be processed
                verify(dumAemExchangeProcess, times(1)).importFromFile(any(File.class));
            } finally {
                System.setProperty("user.dir", originalUserDir);
            }
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle IOException gracefully")
        void shouldHandleIoExceptionGracefully() {
            when(dumAemConfigVarRepository.findById(DumAemOnStartupJpa.FIRST_TIME))
                    .thenReturn(Optional.empty());
            when(dumAemConfigVarRepository.save(any(DumAemConfigVar.class)))
                    .thenReturn(new DumAemConfigVar());

            // This should not throw even if export directory doesn't exist
            dumAemOnStartupJpa.run(applicationArguments);

            verify(dumAemConfigVarRepository).save(any(DumAemConfigVar.class));
        }
    }

    @Nested
    @DisplayName("ApplicationRunner Implementation Tests")
    class ApplicationRunnerImplementationTests {

        @Test
        @DisplayName("Should implement ApplicationRunner interface")
        void shouldImplementApplicationRunnerInterface() {
            assertNotNull(dumAemOnStartupJpa);
            // Verify it's an instance of ApplicationRunner
            assertEquals(true, dumAemOnStartupJpa instanceof org.springframework.boot.ApplicationRunner);
        }

        @Test
        @DisplayName("Should accept ApplicationArguments parameter")
        void shouldAcceptApplicationArgumentsParameter() {
            when(dumAemConfigVarRepository.findById(DumAemOnStartupJpa.FIRST_TIME))
                    .thenReturn(Optional.of(new DumAemConfigVar()));

            // Should not throw with valid ApplicationArguments
            dumAemOnStartupJpa.run(applicationArguments);

            // Add assertion to verify findById was called
            verify(dumAemConfigVarRepository).findById(DumAemOnStartupJpa.FIRST_TIME);
        }

        @Test
        @DisplayName("Should accept null ApplicationArguments")
        void shouldAcceptNullApplicationArguments() {
            when(dumAemConfigVarRepository.findById(DumAemOnStartupJpa.FIRST_TIME))
                    .thenReturn(Optional.of(new DumAemConfigVar()));

            // Should not throw with null ApplicationArguments
            dumAemOnStartupJpa.run(null);

            // Add assertion to verify findById was called
            verify(dumAemConfigVarRepository).findById(DumAemOnStartupJpa.FIRST_TIME);
        }
    }
}
