package com.strilanc.async;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A tool for registering cleanup methods that will need to occur.
 * Invokes registered callbacks when cancelled.
 */
public final class CancelToken {
	/** The states a cancel token can be in. */
	public enum State {
		/** Still-cancellable tokens store callbacks given to them, to be run upon cancellation or discarded upon immortalization. */
		StillCancellable, 
		
		/** Cancelled tokens have run (or are running) any stored callbacks, and will immediately run any new callbacks given to them. */
		Cancelled,
		
		/** Immortal tokens discard, without running, any callbacks that have been or will be given to them. */
		Immortal 
	}
	
	/** Creates and controls a cancel token. */
	public static final class Source {
		/** The token controlled by this source. */
		private final CancelToken token;
		/** The only strong reference to the token's list of callbacks to run when cancelled. */
		@SuppressWarnings("unused") private Node onCancelledCallbacksStrongRef;
		/** The cancellation callbacks aren't reachable from this object, so giving it the finalizer makes the callbacks garbage-collectable sooner. */
		@SuppressWarnings("unused") private Object finalizer;
		
		/** Constructs a cancel token source that controls a new cancel token. */
		public Source() {
			Node onCancelledCallbacks = new Node();
			CancelToken newToken = new CancelToken();
			newToken.state = State.StillCancellable;
			newToken.onCancelledCallbacks = new WeakReference<>(onCancelledCallbacks);
			newToken.onStableCallbacks = new Node();
			
			this.token = newToken;
			this.onCancelledCallbacksStrongRef = onCancelledCallbacks;
			this.finalizer = new Object() {
				@Override protected void finalize() throws Throwable {
					newToken.tryImmortalize();
				}
			};
		}
		
		/** Returns the cancel token controlled by the source. */
		public CancelToken getToken() {
			return token;
		}

		/** Attempts to cancel the token controlled by the source, returning false and doing nothing if the token was not still cancellable. */
		public boolean cancel() {
			boolean didCancel = token.tryCancel();
			if (didCancel) {
				onCancelledCallbacksStrongRef = null;
				finalizer = null;
			}
			return didCancel;
		}
	}

	/** A circular doubly-linked list node, storing a callback. */
	private final static class Node {
		public Node next;
		public Node prev;
		public Runnable callback;
		
		public Node() {
			next = prev = this;
		}
		
		public Node insert(Runnable callbackForNewNode) {
			return new Node(callbackForNewNode, this);
		}
		private Node(Runnable callback, Node next) {
			this.callback = callback;
			this.next = next;
			this.prev = next.prev;
			next.prev = prev.next = this;
		}
		
		public void remove() {
			next.prev = prev;
			prev.next = next;
		}
		
		public void runAll() {
			// assumes `this` is the root, with the only null callback
			for (Node n = next; n != this; n = n.next){
				n.callback.run();
			}
		}
	}
	
	/** State of this token. */
	private State state;
	/** List of actions to run when cancelled. */
	private WeakReference<Node> onCancelledCallbacks;
	/** List of actions to run when cancelled or immortalized. */
	private Node onStableCallbacks;
		
	/** Returns an already-cancelled token. */
	public static CancelToken cancelled() {
		CancelToken token = new CancelToken();
		token.state = State.Cancelled;
		return token;
	}
	
	/** Returns an already-immortal token. */
	public static CancelToken immortal() {
		CancelToken token = new CancelToken();
		token.state = State.Immortal;
		return token;
	}
	
	private CancelToken() {
		// (used by CancelToken.Source class, cancelled() method, and immortal() method)
	}
	
	/** Determines whether the cancel token is still-cancellable, cancelled, or immortal. */
	public State getState() {
		synchronized(this) {
			return state;
		}
	}

	/** Register a callback to be run when the token is cancelled, or runs it right away if the token is already cancelled. */
	public void whenCancelled(Runnable whenCancelledCallback) {
		this.thenDo(whenCancelledCallback);
	}
	
	/** Register a callback to be run, unless the given token is cancelled first, when the receiving token is cancelled. */
	public void whenCancelledBefore(final Runnable whenCancelledCallback, final CancelToken unlessCancelledFirstToken) {
		if (whenCancelledCallback == null) throw new IllegalArgumentException("didCancelCallback == null");
		if (unlessCancelledFirstToken == null) throw new IllegalArgumentException("unlessCancelledFirstToken == null");
		CancelToken trigger = this;
		CancelToken blocker = unlessCancelledFirstToken;
		Runnable callback = whenCancelledCallback;
		
		// Will the cycle just be torn down? Then don't bother.
		if (trigger == blocker) return;
		if (blocker.getState() == State.Cancelled) return;
		
		// register callback
		WeakReference<Node> triggerDoWeak = trigger.thenDo(callback);
		if (triggerDoWeak == null) return; // the trigger ran or never will, and has been discarded already, so we're done
		
		// register removal of callback
		WeakReference<Node> blockerDoWeak = blocker.thenDo(() -> {
			Node triggerDo = triggerDoWeak.get();
			if (triggerDo == null) return;
			synchronized(trigger) {
				triggerDo.remove();
			}
		});
		if (blockerDoWeak == null) return; // the blocker ran or never will, and has been discarded already, so we're done

	    // to weave the cleanup cycle, instead of conditioning to infinity, we forward-declare and use an only-run-on-second-call method
	    Node[] blockerFinallyRef = new Node[1];
	    AtomicBoolean isPrepared = new AtomicBoolean();
	    Runnable prepareElseRemoveBlockerNodes = () -> {
    		if (isPrepared.compareAndSet(false, true)) return;
    		Node blockerDo = blockerDoWeak.get();
    		Node blockerFinally = blockerFinallyRef[0];
    		synchronized(blocker) {
    			if (blockerDo != null) blockerDo.remove();
    			if (blockerFinally != null) blockerFinally.remove();
    		}
	    };
	    
	    // complete the cycle
	    Node triggerFinally = trigger.finallyDo(prepareElseRemoveBlockerNodes);
	    if (triggerFinally != null) {
		    blockerFinallyRef[0] = blocker.finallyDo(() -> {
				synchronized(trigger) {
					triggerFinally.remove();
				}
		    });
	    }
	    prepareElseRemoveBlockerNodes.run();
	}

	private void tryImmortalize() {
		// (used by finalizer in CancelToken.Source)
		Node onStable;
		synchronized(this) {
			if (state != State.StillCancellable) return;
			state = State.Immortal;
			onStable = onStableCallbacks;
			onStableCallbacks = null;
			onCancelledCallbacks = null;
		}

		onStable.runAll();
	}
	
	private boolean tryCancel() {
		// (used by CancelToken.Source#cancel)
		Node onStable;
		Node onCancelled;
		synchronized(this) {
			if (state != State.StillCancellable) return false;
			state = State.Cancelled;
			onStable = onStableCallbacks;
			onCancelled = onCancelledCallbacks.get();
			onStableCallbacks = null;
			onCancelledCallbacks = null;
		}

		onStable.runAll();
		onCancelled.runAll();
		return true;
	}
	
	private WeakReference<Node> thenDo(final Runnable whenCancelledCallback) {
		if (whenCancelledCallback == null) throw new IllegalArgumentException("whenCancelledCallback == null");

		synchronized(this) {
			if (state == State.Immortal) return null;
			if (state == State.StillCancellable) {
				Node n = onCancelledCallbacks.get();
				if (n == null) return null; // our source was collected, so we're about to be immortalized
				return new WeakReference<>(n.insert(whenCancelledCallback));
			}
		}
		
		whenCancelledCallback.run();
		return null;
	}

	private Node finallyDo(final Runnable whenSettledCallback) {
		if (whenSettledCallback == null) throw new IllegalArgumentException("whenSettledCallback == null");

		synchronized(this) {
			if (state == State.StillCancellable) {
				return onStableCallbacks.insert(whenSettledCallback);
			}
		}
		
		whenSettledCallback.run();
		return null;
	}
}
