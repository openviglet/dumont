# Future Improvements

## Defensive Immutability - Low Priority

These objects are **safe today** but have `@Setter` that could cause issues if someone mutates them in a concurrent context (e.g., inside `AemNodeNavigator`'s reactive `flatMap` with parallelism).

### DumAemContext
- **File:** `aem-commons/src/main/java/com/viglet/dumont/connector/aem/commons/bean/DumAemContext.java`
- **Issue:** Has `@Setter` on `dumAemTargetAttr` and `dumAemSourceAttr`
- **Why safe today:** Created fresh per call to `DumAemAttrProcess.prepareAttributeDefs()`, used within a single thread
- **Improvement:** Replace mutable context with method parameters or an immutable record

### DumAemConfiguration.localePaths
- **File:** `aem-commons/src/main/java/com/viglet/dumont/connector/aem/commons/context/DumAemConfiguration.java`
- **Issue:** `Collection<DumAemLocalePathContext> localePaths = new HashSet<>()` exposed via `@Getter`
- **Why safe today:** Populated during construction, never mutated after
- **Improvement:** Return `Collections.unmodifiableCollection()` in the getter or use `List.copyOf()`

### DumAemTargetAttr / DumAemModel
- **Files:**
  - `aem-commons/src/main/java/com/viglet/dumont/connector/aem/commons/mappers/DumAemTargetAttr.java`
  - `aem-commons/src/main/java/com/viglet/dumont/connector/aem/commons/mappers/DumAemModel.java`
- **Issue:** Have `@Setter` on fields like `textValue`, `sourceAttrs`, `targetAttrs`
- **Why safe today:** Only read (not mutated) during reactive parallel processing
- **Improvement:** Remove `@Setter`, use only `@Builder` for construction

### DumAemLocalePathContext
- **File:** `aem-commons/src/main/java/com/viglet/dumont/connector/aem/commons/context/DumAemLocalePathContext.java`
- **Issue:** Has `@Setter` on `snSite`, `locale`, `path`
- **Why safe today:** Stored in `DumAemConfiguration.localePaths`, not mutated after construction
- **Improvement:** Remove `@Setter`, use only `@Builder` for construction
