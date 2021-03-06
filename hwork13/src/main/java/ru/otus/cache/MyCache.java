package ru.otus.cache;

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Function;

public class MyCache<K, V> implements MyCacheMBean<K, V> {
    private static final int TIME_THRESHOLD_MS = 5;

    private int maxElements;
    private long lifeTimeMs;
    private long idleTimeMs;
    private boolean isEternal;

    private final Map<K, SoftReference<MyCacheElement<V>>> elements = new LinkedHashMap<>();
    private final Timer timer = new Timer();

    private int hit = 0;
    private int miss = 0;

    public MyCache(int maxElements, long lifeTimeMs, long idleTimeMs){
        this.maxElements = maxElements;
        this.lifeTimeMs = lifeTimeMs;
        this.idleTimeMs = idleTimeMs;
        this.isEternal = lifeTimeMs == 0 && idleTimeMs == 0;

    }

    public void setParams(int maxElements, long lifeTimeMs, long idleTimeMs, boolean isEternal) {
        Map<K, SoftReference<MyCacheElement<V>>> tempElements = new LinkedHashMap<>();
        tempElements.putAll(elements);
        elements.clear();
        this.maxElements = maxElements;
        this.lifeTimeMs = lifeTimeMs > 0 ? lifeTimeMs : 0;
        this.idleTimeMs = idleTimeMs > 0 ? idleTimeMs : 0;
        this.isEternal = lifeTimeMs == 0 && idleTimeMs == 0 || isEternal;
        tempElements.forEach((K, V)->{
            SoftReference<MyCacheElement<V>> elementReference = elements.get(K);
            try {
                MyCacheElement<V> element = elementReference.get();
                this.put(K,element.getValue());
            } catch (NullPointerException e) {}
        });
        tempElements.clear();
    }

    public void put(K key, V value) {
        if (elements.size() == maxElements) {
            K firstKey = elements.keySet().iterator().next();
            elements.remove(firstKey);
        }
        if (elements.get(key)!= null) {
            elements.remove(key);
        }
        MyCacheElement<V> element = new MyCacheElement<>(value);
        elements.put(key, new SoftReference<>(element));

        if (!isEternal) {
            if (lifeTimeMs != 0) {
                TimerTask lifeTimerTask = getTimerTask(key, lifeElement -> lifeElement.getCreationTime() + lifeTimeMs);
                timer.schedule(lifeTimerTask, lifeTimeMs);
            }
            if (idleTimeMs != 0) {
                TimerTask idleTimerTask = getTimerTask(key, idleElement -> idleElement.getLastAccessTime() + idleTimeMs);
                timer.schedule(idleTimerTask, idleTimeMs, idleTimeMs);
            }
        }
    }

    public V get(K key) {
        SoftReference<MyCacheElement<V>> elementReference = elements.get(key);
        if (elementReference != null) {
            MyCacheElement<V> element = elementReference.get();
            if (element!=null) {
                element.setAccessed();
                hit++;
                return element.getValue();
            }
        }
        miss++;
        return null;
    }

    public int getHitCount() {
        return hit;
    }

    public int getMissCount() {
        return miss;
    }

    public int getmaxElements() {
        return maxElements;
    }

    public long getlifeTimeMs() {
        return lifeTimeMs;
    }

    public long getidleTimeMs() {
        return idleTimeMs;
    }

    public boolean getisEternal() {
        return isEternal;
    }

    @Override
    public void dispose() {
        timer.cancel();
    }

    private TimerTask getTimerTask(final K key, Function<MyCacheElement<V>, Long> timeFunction) {
        return new TimerTask() {
            @Override
            public void run() {
                SoftReference<MyCacheElement<V>> element = elements.get(key);
                if (element == null || isT1BeforeT2(timeFunction.apply(element.get()), System.currentTimeMillis())) {
                    elements.remove(key);
                    this.cancel();
                }
            }
        };
    }

    private boolean isT1BeforeT2(long t1, long t2) {
        return t1 < t2 + TIME_THRESHOLD_MS;
    }

}
