# Job Processing Strategy Pattern

This package contains strategy implementations for processing job items in the Dumont connector. Each strategy handles specific scenarios during the indexing process.

## Strategy Interface

All strategies implement the `JobProcessingStrategy` interface which defines:
- `process()`: Executes the strategy's logic
- `canHandle()`: Determines if the strategy can handle a given job item
- `getPriority()`: Defines execution order (lower values = higher priority)

## Strategy Execution Order

Strategies are evaluated in order of priority. The first strategy where `canHandle()` returns `true` will process the job item.

| Priority | Strategy Class | Description |
|----------|---------------|-------------|
| 10 | DeindexStrategy | Remove objects from the search index |
| 20 | IgnoreIndexingRuleStrategy | Skip objects matching ignore rules |
| 30 | IndexStrategy | Index new objects |
| 40 | ReindexStrategy | Update existing objects with changes |
| 50 | UnchangedStrategy | Handle objects without changes (fallback) |

## Strategy Descriptions

### 1. DeindexStrategy (Priority: 10)

**Purpose:** Removes objects from the search index when a DELETE action is requested.

**Conditions:**
- Job action is `DELETE`

**Actions:**
- Marks the item as `DEINDEXED` in the indexing service
- Adds the delete job to the batch processor
- Logs the deindex operation

### 2. IgnoreIndexingRuleStrategy (Priority: 20)

**Purpose:** Filters out objects that match configured ignore rules based on attribute patterns.

**Conditions:**
- Job action is `CREATE`
- Object attributes match at least one ignore rule pattern (using regex)

**Actions:**
- Marks the item as `IGNORED` in the indexing service
- If the object was previously indexed, creates a DELETE job to remove it from the index
- Logs the ignored status

**Note:** This strategy prevents unwanted content from being indexed by matching patterns against object attributes.

### 3. IndexStrategy (Priority: 30)

**Purpose:** Indexes new objects that don't exist in the indexing database.

**Conditions:**
- Object ID is not empty
- Object does not exist in the indexing service

**Actions:**
- Creates a new indexing record with status `PREPARE_INDEX`
- Adds the job item to the batch processor
- Logs the creation

### 4. ReindexStrategy (Priority: 40)

**Purpose:** Updates existing objects when their content has changed or they were previously ignored.

**Conditions:**
- Object exists in the indexing service
- AND one of the following:
  - Checksum differs from stored checksum (content changed)
  - Current status is `IGNORED` (previously ignored but now valid)

**Actions:**
- Updates the indexing status to `PREPARE_REINDEX`
- Adds the job item to the batch processor
- Logs the reindex operation showing old and new checksums

**Special Handling:**
- If duplicate indexing entries are found, removes them and recreates with status `PREPARE_FORCED_REINDEX`

### 5. UnchangedStrategy (Priority: 50)

**Purpose:** Default fallback strategy for objects that exist but haven't changed.

**Conditions:**
- Object exists in the indexing service
- No other strategy has handled the item (lowest priority)

**Actions:**
- Updates status to `PREPARE_UNCHANGED`
- Logs unchanged status
- Does NOT add to batch processor (no indexing needed)

**Note:** This is the final fallback strategy and ensures all items are properly tracked even when no action is needed.

## Processing Flow

```
Job Item Received
    ↓
Sort strategies by priority (ascending)
    ↓
For each strategy:
    ↓
    canHandle()?
    ↓
    Yes → process() → DONE
    ↓
    No → Try next strategy
    ↓
If no strategy handles → Error
```

## Indexing Statuses

The strategies use various status values defined in `DumIndexingStatus`:

- `PREPARE_INDEX`: Object is new and ready for indexing
- `PREPARE_REINDEX`: Object exists and needs to be reindexed
- `PREPARE_FORCED_REINDEX`: Duplicate entries were cleaned, forcing reindex
- `PREPARE_UNCHANGED`: Object exists and hasn't changed
- `IGNORED`: Object matches ignore rules
- `DEINDEXED`: Object has been removed from the index

## Key Features

1. **Priority-based Execution**: Ensures critical operations (like deletions) are processed first
2. **Rule-based Filtering**: Prevents unwanted content from being indexed
3. **Change Detection**: Only reindexes objects when content actually changes
4. **Duplicate Handling**: Automatically cleans up and fixes duplicate indexing entries
5. **Batch Processing**: Efficiently queues items for bulk indexing operations

## Examples

### Example 1: New Object
- IndexStrategy handles (Priority 30)
- Creates new indexing record
- Queues for indexing

### Example 2: Updated Object
- ReindexStrategy handles (Priority 40)
- Detects checksum difference
- Queues for reindexing

### Example 3: Deleted Object
- DeindexStrategy handles (Priority 10)
- Removes from indexing service
- Queues deletion job

### Example 4: Ignored Object
- IgnoreIndexingRuleStrategy handles (Priority 20)
- Matches ignore rule pattern
- Marks as ignored, possibly removes from index

### Example 5: Unchanged Object
- UnchangedStrategy handles (Priority 50)
- No changes detected
- Updates status but doesn't queue for processing
