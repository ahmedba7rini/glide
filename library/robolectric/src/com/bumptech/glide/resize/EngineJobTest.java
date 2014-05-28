package com.bumptech.glide.resize;

import android.os.Handler;
import com.bumptech.glide.resize.cache.MemoryCache;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class EngineJobTest {
    private static final String ID = "asdfas";
    private EngineJobHarness harness;

    @Before
    public void setUp() {
        harness = new EngineJobHarness();
    }

    @Test
    public void testOnResourceReadyPassedToCallbacks() throws Exception {
        harness.job.onResourceReady(harness.resource);

        Robolectric.runUiThreadTasks();
        verify(harness.cb).onResourceReady(eq(harness.resource));
    }

    @Test
    public void testListenerNotifiedJobCompleteOnOnResourceReady() {
        harness.job.onResourceReady(harness.resource);

        Robolectric.runUiThreadTasks();

        verify(harness.listener).onEngineJobComplete(eq(ID));
    }

    @Test
    public void testResourceAddedToCacheOnResourceReady() {
        harness.job.onResourceReady(harness.resource);

        Robolectric.runUiThreadTasks();
        verify(harness.memoryCache).put(eq(ID), eq(harness.resource));
    }

    @Test
    public void testOnExceptionPassedToCallbacks() throws Exception {
        Exception exception = new Exception("Test");

        harness.job.onException(exception);

        Robolectric.runUiThreadTasks();
        verify(harness.cb).onException(eq(exception));
    }

    @Test
    public void testListenerNotifiedJobCompleteOnException() {
        harness.job.onException(new Exception("test"));

        Robolectric.runUiThreadTasks();
        verify(harness.listener).onEngineJobComplete(eq(ID));
    }

    @Test
    public void testListenerNotifiedOfCancelOnCancel() {
        harness.job.cancel();

        verify(harness.listener).onEngineJobCancelled(eq(ID));
    }

    @Test
    public void testOnResourceReadyNotDeliveredAfterCancel() {
        harness.job.cancel();

        harness.job.onResourceReady(harness.resource);

        Robolectric.runUiThreadTasks();
        verify(harness.cb, never()).onResourceReady(eq(harness.resource));
    }

    @Test
    public void testOnExceptionNotDeliveredAfterCancel() {
        harness.job.cancel();

        harness.job.onException(new Exception("test"));

        Robolectric.runUiThreadTasks();
        verify(harness.cb, never()).onException(any(Exception.class));
    }

    @Test
    public void testRemovingAllCallbacksCancelsRunner() {
        harness.job.removeCallback(harness.cb);

        assertTrue(harness.job.isCancelled());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void removingSomeCallbacksDoesNotCancelRunner() {
        harness.job.addCallback(mock(ResourceCallback.class));
        harness.job.removeCallback(harness.cb);

        assertFalse(harness.job.isCancelled());
    }

    @Test
    public void testResourceIsAcquiredOncePerConsumerAndHeldAndReleasedBeforeAndAfterNotifyingConsumers() {
        harness.job.onResourceReady(harness.resource);

        verify(harness.referenceCounter, times(3)).acquireResource(eq(harness.resource));
        verify(harness.referenceCounter, times(1)).releaseResource(eq(harness.resource));
    }

    @Test
    public void testResourceIsNotRecycledIfMemoryCacheEvictsSynchronously() {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                harness.referenceCounter.releaseResource(harness.resource);
                return null;
            }
        }).when(harness.memoryCache).put(eq(ID), eq(harness.resource));

        harness.job.onResourceReady(harness.resource);
        verify(harness.referenceCounter, times(3)).acquireResource(eq(harness.resource));
        verify(harness.referenceCounter, times(2)).releaseResource(harness.resource);
    }

    @Test
    public void testDoesNotNotifyCancelledIfCompletesBeforeCancel() {
        harness.job.onResourceReady(harness.resource);
        harness.job.cancel();

        verify(harness.listener, never()).onEngineJobCancelled(eq(ID));
    }

    @Test
    public void testDoesNotNotifyCancelledIfAlreadyCancelled() {
        harness.job.cancel();
        harness.job.cancel();

        verify(harness.listener, times(1)).onEngineJobCancelled(eq(ID));
    }

    @SuppressWarnings("unchecked")
    private static class EngineJobHarness {
        MemoryCache memoryCache = mock(MemoryCache.class);
        Handler mainHandler = new Handler();
        ResourceCallback<Object> cb = mock(ResourceCallback.class);
        Resource<Object> resource = mock(Resource.class);
        EngineJobListener listener = mock(EngineJobListener.class);
        ResourceReferenceCounter referenceCounter = mock(ResourceReferenceCounter.class);

        EngineJob <Object> job = new EngineJob<Object>(ID, memoryCache, mainHandler, referenceCounter, listener);

        public EngineJobHarness() {
            job.addCallback(cb);
        }
    }
}
