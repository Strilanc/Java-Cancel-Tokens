package test.com.strilanc.async;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.strilanc.async.CancelToken;

public class CancelTokenTest {
	private static void forceFinalization() {
		Runtime.getRuntime().gc();
		Runtime.getRuntime().runFinalization();
	}
	
	@Test public void testCancelledToken() {
	    CancelToken c = CancelToken.cancelled();
	    assertEquals(CancelToken.State.Cancelled, c.getState());
	    
	    AtomicInteger i = new AtomicInteger();
	    c.whenCancelled(i::getAndIncrement);
	    assertEquals(1, i.get());
	}

	@Test public void testImmortalToken() {
	    CancelToken c = CancelToken.immortal();
	    assertEquals(CancelToken.State.Immortal, c.getState());
	    
	    AtomicInteger i = new AtomicInteger();
	    c.whenCancelled(i::getAndIncrement);
	    assertEquals(0, i.get());
	}

	@Test public void testControlledToken_states() {
	    CancelToken.Source s = new CancelToken.Source();
	    CancelToken c = s.getToken();
	    assertEquals(CancelToken.State.StillCancellable, c.getState());
	    
	    assertTrue(s.cancel());
	    assertEquals(CancelToken.State.Cancelled, c.getState());
	    
	    assertFalse(s.cancel());
	    assertEquals(CancelToken.State.Cancelled, c.getState());
	}
	@Test public void testControlledToken_whenCancelled() {
	    CancelToken.Source s = new CancelToken.Source();
	    CancelToken c = s.getToken();
	    AtomicInteger i = new AtomicInteger();

	    c.whenCancelled(i::getAndIncrement);
	    assertEquals(0, i.get());
	    
	    s.cancel();
	    assertEquals(1, i.get());
	    
	    c.whenCancelled(i::getAndIncrement);
	    assertEquals(2, i.get());
	}
	
	@Test public void testControlledTokenCanBecomeImmortal() {
        CancelToken.Source s = new CancelToken.Source();
	    final CancelToken c = s.getToken();
	    
	    final AtomicInteger i = new AtomicInteger();
	    c.whenCancelled(i::getAndIncrement);
	    
	    forceFinalization();
	    assertEquals(CancelToken.State.StillCancellable, c.getState());
	    
	    s = null;
	    forceFinalization();
	    assertEquals(CancelToken.State.Immortal, c.getState());
	}
	
	@Test public void testCallbacksGetDiscardedWhenControlledTokenBecomesImmortal() {
        CancelToken.Source s = new CancelToken.Source();
	    final CancelToken c = s.getToken();
	    
	    final AtomicInteger i = new AtomicInteger();
	    c.whenCancelled(new Runnable() {
			@Override public void run() {}
			@Override protected void finalize() throws Throwable {
				i.getAndIncrement();
			}
		});
	    
	    forceFinalization();
    	assertEquals(0, i.get());

    	s = null;
	    forceFinalization();
    	assertEquals(1, i.get());
	}
	
	@Test public void testBackAndForthConditionalCancellation() {
	    CancelToken.Source s = new CancelToken.Source();
	    CancelToken.Source u = new CancelToken.Source();
	    
	    AtomicInteger si = new AtomicInteger();
	    AtomicInteger ui = new AtomicInteger();
	    s.getToken().whenCancelledBefore(si::getAndIncrement, u.getToken());
	    u.getToken().whenCancelledBefore(ui::getAndIncrement, s.getToken());

	    assertEquals(0, si.get());
	    assertEquals(0, ui.get());
	    
	    s.cancel();
	    assertEquals(1, si.get());
	    assertEquals(0, ui.get());

	    u.cancel();
	    assertEquals(1, si.get());
	    assertEquals(0, ui.get());
	}

	@Test public void testConditionalCallbacksGetDiscardedWhenTokensBecomeImmortal() {
	    final AtomicInteger i1 = new AtomicInteger();
	    final AtomicInteger i2 = new AtomicInteger();
	    
        CancelToken.Source s1 = new CancelToken.Source();
        CancelToken.Source s2 = new CancelToken.Source();
	    CancelToken c1 = s1.getToken();
        CancelToken c2 = s2.getToken();
	        
	    c1.whenCancelledBefore(new Runnable() {
			@Override public void run() {}
			@Override protected void finalize() throws Throwable {
				i1.getAndIncrement();
			}
		}, c2);
	    c2.whenCancelledBefore(new Runnable() {
			@Override public void run() {}
			@Override protected void finalize() throws Throwable {
				i2.getAndIncrement();
			}
		}, c1);

	    forceFinalization();
    	assertEquals(0, i1.get());
    	assertEquals(0, i2.get());

    	s1 = null;
	    forceFinalization();
    	assertEquals(1, i1.get());
    	assertEquals(0, i2.get());

    	s2 = null;
	    forceFinalization();
    	assertEquals(1, i1.get());
    	assertEquals(1, i2.get());
	}

	@Test public void testConditionalCallbacksGetDiscardedWhenTokensGetCancelled() {
	    final AtomicInteger i1 = new AtomicInteger();
	    final AtomicInteger i2 = new AtomicInteger();
	    final AtomicInteger i3 = new AtomicInteger();
	    final AtomicInteger i4 = new AtomicInteger();
	    
        CancelToken.Source s1 = new CancelToken.Source();
        CancelToken.Source s2 = new CancelToken.Source();
	    CancelToken c1 = s1.getToken();
        CancelToken c2 = s2.getToken();
	        
	    c1.whenCancelledBefore(new Runnable() {
			@Override public void run() {
				i1.getAndIncrement();
			}
			@Override protected void finalize() throws Throwable {
				i2.getAndIncrement();
			}
		}, c2);
	    c2.whenCancelledBefore(new Runnable() {
			@Override public void run() {
				i3.getAndIncrement();
			}
			@Override protected void finalize() throws Throwable {
				i4.getAndIncrement();
			}
		}, c1);

	    forceFinalization();
    	assertEquals(0, i1.get());
    	assertEquals(0, i2.get());
    	assertEquals(0, i3.get());
    	assertEquals(0, i4.get());

    	s1.cancel();
	    forceFinalization();
    	assertEquals(1, i1.get());
    	assertEquals(1, i2.get());
    	assertEquals(0, i3.get());
    	assertEquals(1, i4.get());

    	s2.cancel();
	    forceFinalization();
    	assertEquals(1, i1.get());
    	assertEquals(1, i2.get());
    	assertEquals(0, i3.get());
    	assertEquals(1, i4.get());
	}

	@Test public void testConditionalCallbacksGetDiscardedWhenTokensGetImmortalThenCancelled() {
	    final AtomicInteger i1 = new AtomicInteger();
	    final AtomicInteger i2 = new AtomicInteger();
	    final AtomicInteger i3 = new AtomicInteger();
	    final AtomicInteger i4 = new AtomicInteger();
	    
        CancelToken.Source s1 = new CancelToken.Source();
        CancelToken.Source s2 = new CancelToken.Source();
	    CancelToken c1 = s1.getToken();
        CancelToken c2 = s2.getToken();
	        
	    c1.whenCancelledBefore(new Runnable() {
			@Override public void run() {
				i1.getAndIncrement();
			}
			@Override protected void finalize() throws Throwable {
				i2.getAndIncrement();
			}
		}, c2);
	    c2.whenCancelledBefore(new Runnable() {
			@Override public void run() {
				i3.getAndIncrement();
			}
			@Override protected void finalize() throws Throwable {
				i4.getAndIncrement();
			}
		}, c1);

	    forceFinalization();
    	assertEquals(0, i1.get());
    	assertEquals(0, i2.get());
    	assertEquals(0, i3.get());
    	assertEquals(0, i4.get());

    	s1 = null;
	    forceFinalization();
    	assertEquals(0, i1.get());
    	assertEquals(1, i2.get());
    	assertEquals(0, i3.get());
    	assertEquals(0, i4.get());

    	s2.cancel();
	    forceFinalization();
    	assertEquals(0, i1.get());
    	assertEquals(1, i2.get());
    	assertEquals(1, i3.get());
    	assertEquals(1, i4.get());
	}

	@Test public void testConditionStayingUncancelledAllowsCallback() {
        CancelToken.Source t = new CancelToken.Source();
        CancelToken.Source b = new CancelToken.Source();
	        
	    AtomicInteger i = new AtomicInteger();
	    t.getToken().whenCancelledBefore(i::getAndIncrement, b.getToken());

	    assertEquals(0, i.get());
    	t.cancel();
    	assertEquals(1, i.get());
	}
	@Test public void testConditionBecomingImmortalAllowsCallback() {
        CancelToken.Source t = new CancelToken.Source();
        CancelToken.Source b = new CancelToken.Source();
        CancelToken c = b.getToken();
	        
	    AtomicInteger i = new AtomicInteger();
	    t.getToken().whenCancelledBefore(i::getAndIncrement, c);

	    b = null;
	    forceFinalization();
	    assertEquals(CancelToken.State.Immortal, c.getState());
    	
	    assertEquals(0, i.get());
    	t.cancel();
    	assertEquals(1, i.get());
	}
	@Test public void testConditionStartingImmortalAllowsCallback() {
        CancelToken.Source t = new CancelToken.Source();
	        
	    AtomicInteger i = new AtomicInteger();
	    t.getToken().whenCancelledBefore(i::getAndIncrement, CancelToken.immortal());
    	
	    assertEquals(0, i.get());
    	t.cancel();
    	assertEquals(1, i.get());
	}
	@Test public void testConditionStartingCancelledPreventsCallback() {
        CancelToken.Source t = new CancelToken.Source();
	        
	    AtomicInteger i = new AtomicInteger();
	    t.getToken().whenCancelledBefore(i::getAndIncrement, CancelToken.cancelled());
    	
	    assertEquals(0, i.get());
    	t.cancel();
    	assertEquals(0, i.get());
	}
	@Test public void testConditionBecomingCancelledPreventsCallback() {
        CancelToken.Source t = new CancelToken.Source();
        CancelToken.Source b = new CancelToken.Source();
	        
	    AtomicInteger i = new AtomicInteger();
	    t.getToken().whenCancelledBefore(i::getAndIncrement, b.getToken());

	    b.cancel();
	    assertEquals(0, i.get());
	    
    	t.cancel();
    	assertEquals(0, i.get());
	}
	
	@Test public void testConditioningOnSelfIsConsistent_CancelledBeforeVsAfter() {
	    CancelToken.Source s = new CancelToken.Source();
	    
	    AtomicInteger hit1 = new AtomicInteger();
	    s.getToken().whenCancelledBefore(hit1::getAndIncrement, s.getToken());
	    s.cancel();
	    
	    AtomicInteger hit2 = new AtomicInteger();
	    s.getToken().whenCancelledBefore(hit2::getAndIncrement, s.getToken());
	    
	    assertTrue(hit1.get() <= 1);
	    assertTrue(hit2.get() <= 1);
	    assertTrue(hit1.get() == hit2.get());
	}

	@Test public void testConditionCancelledBeatsActionCancelledWhenThereIsATie() {
	    AtomicInteger hit1 = new AtomicInteger();
	    CancelToken.Source s1 = new CancelToken.Source();
	    s1.cancel();
	    s1.getToken().whenCancelledBefore(hit1::getAndIncrement, CancelToken.cancelled());
	    CancelToken.cancelled().whenCancelledBefore(hit1::getAndIncrement, CancelToken.cancelled()); 
	    CancelToken.cancelled().whenCancelledBefore(hit1::getAndIncrement, s1.getToken()); 
	    assertEquals(0, hit1.get());
	}
}
