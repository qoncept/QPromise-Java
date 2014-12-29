package jp.co.qoncept.promise;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Timer;
import java.util.TimerTask;

import jp.co.qoncept.functional.Consumer;
import jp.co.qoncept.functional.Function;
import jp.co.qoncept.functional.Supplier;
import jp.co.qoncept.util.Tuple3;

import org.junit.Test;

public class PromiseTest {
	public Promise<Integer> asyncSucceed(final Integer value) {
		return new Promise<Integer>(
				new Consumer<Tuple3<? extends Consumer<? super Integer>, Consumer<? super Exception>, Consumer<? super Promise<Integer>>>>() {
					@Override
					public void accept(
							final Tuple3<? extends Consumer<? super Integer>, Consumer<? super Exception>, Consumer<? super Promise<Integer>>> executor) {
						new Timer().schedule(new TimerTask() {
							@Override
							public void run() {
								executor.get0().accept(value + 1);
							}
						}, 100L);
					}
				});
	}

	public Promise<Integer> asyncFail(final Exception reason) {
		return new Promise<Integer>(
				new Consumer<Tuple3<? extends Consumer<? super Integer>, Consumer<? super Exception>, Consumer<? super Promise<Integer>>>>() {
					@Override
					public void accept(
							final Tuple3<? extends Consumer<? super Integer>, Consumer<? super Exception>, Consumer<? super Promise<Integer>>> executor) {
						new Timer().schedule(new TimerTask() {
							@Override
							public void run() {
								executor.get1().accept(reason);
							}
						}, 100L);
					}
				});
	}

	@Test
	public void testThen() {
		final Exception error = new Exception();
		final boolean[] reached = new boolean[1];

		reached[0] = false;
		wait(asyncSucceed(0).then(new Function<Integer, Promise<Void>>() {
			@Override
			public Promise<Void> apply(Integer value) {
				assertEquals(1, value.intValue());
				reached[0] = true;
				return Promise.fulfill(null);
			}
		}, new Function<Exception, Promise<Void>>() {
			@Override
			public Promise<Void> apply(Exception reason) {
				fail("Never reaches here.");
				return Promise.reject(reason);
			}
		}));
		assertTrue(reached[0]);

		reached[0] = false;
		wait(asyncFail(error).then(new Function<Integer, Promise<Void>>() {
			@Override
			public Promise<Void> apply(Integer value) {
				fail("Never reaches here.");
				return Promise.fulfill(null);
			}
		}, new Function<Exception, Promise<Void>>() {
			@Override
			public Promise<Void> apply(Exception reason) {
				assertEquals(error, reason);
				reached[0] = true;
				return null;
			}
		}));
		assertTrue(reached[0]);
	}

	@Test
	public void testCatch() {
		final Exception error = new Exception();
		final int[] reach = new int[1];

		wait(asyncSucceed(0).catch_(
				new Function<Exception, Promise<Integer>>() {
					@Override
					public Promise<Integer> apply(Exception t) {
						fail("Never reaches here.");
						return null;
					}
				}));

		reach[0] = 0;
		wait(asyncFail(error).catch_(
				new Function<Exception, Promise<Integer>>() {
					@Override
					public Promise<Integer> apply(Exception reason) {
						assertEquals(error, reason);
						reach[0]++;
						return null;
					}
				}));
		assertEquals(1, reach[0]);

		// fall through
		reach[0] = 0;
		wait(asyncFail(error).catch_(
				new Function<Exception, Promise<Integer>>() {
					@Override
					public Promise<Integer> apply(Exception reason) {
						assertEquals(error, reason);
						reach[0]++;
						return null;
					}
				}).catch_(new Function<Exception, Promise<Integer>>() {
			@Override
			public Promise<Integer> apply(Exception reason) {
				assertEquals(error, reason);
				reach[0]++;
				return null;
			}
		}));
		assertEquals(2, reach[0]);

		// recovery
		reach[0] = 0;
		wait(asyncFail(error).catch_(
				new Function<Exception, Promise<Integer>>() {
					@Override
					public Promise<Integer> apply(Exception reason) {
						assertEquals(error, reason);
						reach[0]++;
						return asyncSucceed(100);
					}
				}).then(new Function<Integer, Promise<Void>>() {
			@Override
			public Promise<Void> apply(Integer value) {
				assertEquals(101, value.intValue());
				reach[0]++;
				return Promise.fulfill(null);
			}
		}));
		assertEquals(2, reach[0]);

		// new reason
		final Exception error2 = new Exception();
		reach[0] = 0;
		wait(asyncFail(error).catch_(
				new Function<Exception, Promise<Integer>>() {
					@Override
					public Promise<Integer> apply(Exception reason) {
						assertEquals(error, reason);
						reach[0]++;
						return asyncFail(error2);
					}
				}).catch_(new Function<Exception, Promise<Integer>>() {
			@Override
			public Promise<Integer> apply(Exception reason) {
				assertNotEquals(error, reason);
				assertEquals(error2, reason);
				reach[0]++;
				return null;
			}
		}));
		assertEquals(2, reach[0]);
	}

	@Test
	public void testFinally() {
		final Exception error = new Exception();
		final Exception error2 = new Exception();
		final int[] reach = new int[1];

		reach[0] = 0;
		wait(asyncSucceed(1).finally_(new Runnable() {
			@Override
			public void run() {
				reach[0]++;
			}
		}));
		assertEquals(1, reach[0]);

		reach[0] = 0;
		wait(asyncFail(new Exception()).finally_(new Runnable() {
			@Override
			public void run() {
				reach[0]++;
			}
		}));
		assertEquals(1, reach[0]);

		// value fall through
		reach[0] = 0;
		wait(asyncSucceed(0).finally_(new Runnable() {
			@Override
			public void run() {
				reach[0]++;
			}
		}).then(new Function<Integer, Promise<Void>>() {
			@Override
			public Promise<Void> apply(Integer value) {
				assertEquals(1, value.intValue());
				reach[0]++;
				return Promise.fulfill(null);
			}
		}));
		assertEquals(2, reach[0]);

		// update value
		reach[0] = 0;
		wait(asyncSucceed(0).finally_(new Supplier<Promise<Integer>>() {
			@Override
			public Promise<Integer> get() {
				reach[0]++;
				return asyncSucceed(100);
			}

		}).then(new Function<Integer, Promise<Void>>() {
			@Override
			public Promise<Void> apply(Integer value) {
				assertEquals(101, value.intValue());
				reach[0]++;
				return Promise.fulfill(null);
			}
		}));
		assertEquals(2, reach[0]);

		// recovery
		reach[0] = 0;
		wait(asyncFail(error).finally_(new Supplier<Promise<Integer>>() {
			@Override
			public Promise<Integer> get() {
				reach[0]++;
				return asyncSucceed(100);
			}
		}).then(new Function<Integer, Promise<Void>>() {
			@Override
			public Promise<Void> apply(Integer value) {
				assertEquals(101, value.intValue());
				reach[0]++;
				return Promise.fulfill(null);
			}
		}));
		assertEquals(2, reach[0]);

		// reason fall through
		reach[0] = 0;
		wait(asyncFail(error).finally_(new Runnable() {
			@Override
			public void run() {
				reach[0]++;
			}
		}).catch_(new Consumer<Exception>() {
			@Override
			public void accept(Exception reason) {
				assertEquals(error, reason);
				reach[0]++;
			}
		}));
		assertEquals(2, reach[0]);

		// new reason
		reach[0] = 0;
		wait(asyncFail(error).finally_(new Supplier<Promise<Integer>>() {
			@Override
			public Promise<Integer> get() {
				reach[0]++;
				return asyncFail(error2);
			}
		}).catch_(new Function<Exception, Promise<Integer>>() {
			@Override
			public Promise<Integer> apply(Exception reason) {
				assertNotEquals(error, reason);
				assertEquals(error2, reason);
				reach[0]++;
				return null;
			}
		}));
		assertEquals(2, reach[0]);

		// make fail
		reach[0] = 0;
		wait(asyncSucceed(0).finally_(new Supplier<Promise<Integer>>() {
			@Override
			public Promise<Integer> get() {
				reach[0]++;
				return asyncFail(error);
			}
		}).catch_(new Function<Exception, Promise<Integer>>() {
			@Override
			public Promise<Integer> apply(Exception reason) {
				assertEquals(error, reason);
				reach[0]++;
				return null;
			}
		}));
		assertEquals(2, reach[0]);
	}

	@Test
	public void testThenCatchAndFinally() {
		final Exception error = new Exception();
		final int[] reach = new int[1];

		reach[0] = 0;
		wait(asyncSucceed(0).then(new Function<Integer, Promise<Integer>>() {
			@Override
			public Promise<Integer> apply(Integer value) {
				assertEquals(1, value.intValue());
				reach[0]++;
				return asyncSucceed(value);
			}
		}).then(new Function<Integer, Promise<Integer>>() {
			@Override
			public Promise<Integer> apply(Integer value) {
				assertEquals(2, value.intValue());
				reach[0]++;
				return asyncSucceed(value);
			}
		}).then(new Consumer<Integer>() {
			@Override
			public void accept(Integer value) {
				assertEquals(3, value.intValue());
				reach[0]++;
			}
		}).catch_(new Consumer<Exception>() {
			@Override
			public void accept(Exception reason) {
				fail("Never reaches here.");
			}
		}).finally_(new Runnable() {
			@Override
			public void run() {
				reach[0]++;
			}
		}));
		assertEquals(4, reach[0]);

		reach[0] = 0;
		wait(asyncFail(error).then(new Function<Integer, Promise<Integer>>() {
			@Override
			public Promise<Integer> apply(Integer value) {
				fail("Never reaches here.");
				return asyncSucceed(value);
			}
		}).then(new Function<Integer, Promise<Integer>>() {
			@Override
			public Promise<Integer> apply(Integer value) {
				fail("Never reaches here.");
				return asyncSucceed(value);
			}
		}).then(new Function<Integer, Promise<Integer>>() {
			@Override
			public Promise<Integer> apply(Integer t) {
				fail("Never reaches here.");
				return Promise.fulfill(null);
			}
		}).catch_(new Consumer<Exception>() {
			@Override
			public void accept(Exception reason) {
				reach[0]++;
				assertEquals(error, reason);
			}
		}).finally_(new Runnable() {
			@Override
			public void run() {
				reach[0]++;
			}
		}));
		assertEquals(2, reach[0]);

		reach[0] = 0;
		wait(asyncFail(error).then(new Function<Integer, Promise<Integer>>() {
			@Override
			public Promise<Integer> apply(Integer value) {
				fail("Never reaches here.");
				return asyncSucceed(value);
			}
		}).then(new Function<Integer, Promise<Integer>>() {
			@Override
			public Promise<Integer> apply(Integer value) {
				fail("Never reaches here.");
				return asyncSucceed(value);
			}

		}, new Function<Exception, Promise<Integer>>() {
			@Override
			public Promise<Integer> apply(Exception reason) {
				assertEquals(error, reason);
				reach[0]++;
				return asyncSucceed(100);
			}
		}).then(new Function<Integer, Promise<Void>>() {
			@Override
			public Promise<Void> apply(Integer value) {
				assertEquals(101, value.intValue());
				reach[0]++;
				return Promise.fulfill(null);
			}
		}).catch_(new Consumer<Exception>() {
			@Override
			public void accept(Exception t) {
				fail("Never reaches here.");
			}
		}).finally_(new Runnable() {
			@Override
			public void run() {
				reach[0]++;
			}
		}));
		assertEquals(3, reach[0]);
	}

	private static <T> void wait(Promise<T> promise) {
		final boolean[] finished = { false };

		promise.finally_(new Runnable() {
			@Override
			public void run() {
				finished[0] = true;
			}
		});

		while (!finished[0]) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				fail(e.getMessage());
			}
		}
	}
}
