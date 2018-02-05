package org.rtb.vexing.adapter;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AdapterCatalog {

    private final Map<String, Adapter> adapters;

    public AdapterCatalog(List<Adapter> adapterList) {
        this.adapters = adapterList.stream().collect(Collectors.toMap(Adapter::code, Function.identity()));
    }

    public Adapter getByCode(String code) {
        return adapters.get(code);
    }

    public boolean isValidCode(String code) {
        return adapters.containsKey(code);
    }
}
