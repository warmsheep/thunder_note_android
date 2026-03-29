package com.flashnote.java.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import com.flashnote.java.data.model.ApiResponse;
import com.flashnote.java.data.model.FileUploadResult;
import com.flashnote.java.data.remote.FileService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;

import okhttp3.MultipartBody;
import retrofit2.Call;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FileRepositoryImplTest {

    @Mock
    FileService fileService;

    @Mock
    Call<ApiResponse<FileUploadResult>> uploadCall;

    private Context context;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        context = RuntimeEnvironment.getApplication();
        when(fileService.upload(any())).thenReturn(uploadCall);
    }

    @Test
    public void upload_usesDetectedImageMimeTypeInsteadOfOctetStream() throws Exception {
        File imageFile = new File(context.getCacheDir(), "upload-test.png");
        try (FileOutputStream outputStream = new FileOutputStream(imageFile)) {
            outputStream.write(new byte[]{1, 2, 3});
        }

        FileRepositoryImpl repository = new FileRepositoryImpl(fileService, context);
        repository.upload(imageFile, new FileRepository.FileCallback() {
            @Override
            public void onSuccess(String path) {
            }

            @Override
            public void onError(String message, int code) {
            }
        });

        ArgumentCaptor<MultipartBody.Part> captor = ArgumentCaptor.forClass(MultipartBody.Part.class);
        verify(fileService).upload(captor.capture());
        MultipartBody.Part part = captor.getValue();
        assertNotNull(part);
        assertEquals("image/png", part.body().contentType().toString());
    }
}
