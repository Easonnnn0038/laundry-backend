package com.laundry.api.service;

import com.laundry.api.utils.CurrentUserUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhotoServiceTest {
    @TempDir Path root;

    @Test
    void cleanupDeletesOnlyExpiredUnreferencedFiles() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PhotoService service = new PhotoService(jdbc, mock(CurrentUserUtil.class));
        ReflectionTestUtils.setField(service, "uploadDir", root.toString());
        ReflectionTestUtils.setField(service, "urlPrefix", "/photos");
        ReflectionTestUtils.setField(service, "orphanRetentionHours", 24L);
        ReflectionTestUtils.setField(service, "capacityWarningMb", 10240L);
        service.init();

        Path dated = Files.createDirectories(root.resolve("20261001"));
        Path orphan = Files.writeString(dated.resolve("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.jpg"), "orphan");
        Path referenced = Files.writeString(dated.resolve("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.jpg"), "referenced");
        FileTime old = FileTime.from(Instant.now().minusSeconds(48 * 3600));
        Files.setLastModifiedTime(orphan, old);
        Files.setLastModifiedTime(referenced, old);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any()))
                .thenAnswer(call -> call.getArgument(2, Object.class).toString().contains("aaaaaaaa") ? 0 : 1);

        service.cleanupOrphans();

        assertFalse(Files.exists(orphan));
        assertTrue(Files.exists(referenced));
    }
}
