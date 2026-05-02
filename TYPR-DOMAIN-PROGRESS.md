# Typr Domain - Progress Tracker

## Architecture

```
                    ┌─────────────────┐
                    │  TYPR DOMAIN    │
                    │                 │
                    │  Person         │
                    │  Address        │
                    │  Order          │
                    │  ...            │
                    └────────┬────────┘
                             │
           ┌─────────────────┼─────────────────┐
           │                 │                 │
           ▼                 ▼                 ▼
    ┌──────────┐      ┌──────────┐      ┌──────────┐
    │ Database │      │   APIs   │      │  Events  │
    │          │      │          │      │          │
    │ PersonRow│      │PersonDto │      │PersonAvro│
    │.toDomain │      │.toDomain │      │.toDomain │
    │.fromDom  │      │.fromDom  │      │.fromDom  │
    └──────────┘      └──────────┘      └──────────┘
```

**Domain is the hub. Boundaries are spokes.**

---

## Priority Stack

### P0: Foundation
- [x] Domain type DSL (Bridge core)
- [x] Name alignment engine
- [x] Matching rules engine
- [x] toDomain/fromDomain generation

### P1: Core Validation
- [x] Field coverage checks
- [x] Type compatibility checks
- [ ] Missing value analysis
- [ ] Nested type resolution

### P2: Smart Infrastructure
- [ ] Domain-centric repositories (return domain, not rows)
- [ ] Domain-centric services

### P3: Full Generation
- [ ] CRUD endpoints
- [ ] Event publishers/consumers
- [ ] gRPC services

---

## Boundary Integration Status

### Database Boundary
- [x] PostgreSQL
- [x] MariaDB/MySQL
- [x] Oracle
- [x] SQL Server
- [x] DuckDB
- [x] DB2

### API Boundary (OpenAPI)
- [x] Schema parsing
- [x] Type extraction
- [x] Field alignment
- [x] Mapper generation
- [x] TUI browsing

### Events Boundary (Avro/Kafka)
- [x] Schema parsing (AvroParser)
- [x] Type extraction (AvroTypes)
- [x] TUI source loading (SourceLoader)
- [x] TUI field extraction (ProjectionFieldExtractor)
- [x] TUI schema browser (AvroBrowser)
- [x] Type narrowing/compatibility (TypeNarrower)
- [x] Mapper generation (FileBridgeProjectionMapper with ExternalRecord)
- [x] Validation (FlowValidator with SourceEntityType.Record)

### RPC Boundary (gRPC/Protobuf)
- [x] Protobuf parsing (existing in typr/grpc/)
- [x] TUI source loading
- [x] TUI field extraction
- [x] TUI schema browser (ProtoBrowser)
- [x] Type narrowing/compatibility
- [x] Mapper generation
- [x] Validation

---

## Next Steps

### P1: Core Validation (Remaining)
- [ ] Missing value analysis (detect fields that need defaults)
- [ ] Nested type resolution (domain types referencing other domain types)
- [ ] External contract ownership model (see below)

### P1.5: Contract Ownership & In/Out Types

**Key Insight:** Some contracts are external (we consume but can't change), some are ours (we define).

| Direction | Ownership | Generated Code | Validation |
|-----------|-----------|----------------|------------|
| `In` | External | `toDomain()` only | Warning if missing |
| `Out` | Ours | `fromDomain()` only | Error if missing |
| `InOut` | Ours | Both | Error if missing |

**Automatic Direction Detection:**

APIs are functions with inputs and outputs - we can detect direction automatically:
```
POST /customers
  requestBody: CustomerCreate    → In (we receive)
  response: CustomerResponse     → Out (we send)

External API call (reversed):
  request: we send              → Out from domain
  response: we receive          → In to domain
```

| Source | Detection Method |
|--------|------------------|
| OpenAPI | Schema position in request vs response |
| gRPC | Message position in service method |
| Avro | Producer vs consumer role |
| Database | Always InOut |

**Tasks:**
- [ ] Auto-detect In/Out from OpenAPI schema positions
- [ ] Auto-detect In/Out from gRPC service definitions
- [ ] Add `ownership: external | internal` flag to SourceDeclaration
- [ ] Suppress `toSource()` generation for external contracts
- [ ] Different validation severity based on ownership
- [ ] Contract versioning for external schemas
- [ ] Adapter generation for external → internal mapping

**Use Cases:**
- Partner API webhooks (In, external) - adapt to their schema
- Your public API (Out, internal) - you define the contract
- Calling external API (Out=request, In=response)
- Third-party event streams (In, external) - consume but can't change
- Your database (InOut, internal) - full control

### P2: Smart Infrastructure
- [ ] Domain-centric repositories (return domain types, not rows)
- [ ] Domain-centric services (orchestrate across boundaries)

### P3: Full Generation
- [ ] CRUD endpoints (REST from domain types)
- [ ] Event publishers/consumers (Kafka from domain types)
- [ ] gRPC services (from domain types)

---

## Completed Work Log

### 2026-02-08: gRPC/Protobuf Integration Complete
- Added SourceStatus.ReadyProto to TuiState
- Implemented loadProtoSource() in SourceLoader
- Added extractFromProto() and formatProtoType() to ProjectionFieldExtractor
- Added ExtractedSourceType.Message and SourceEntityType.Message
- Updated getAvailableEntities() for Proto sources
- Updated allReadySources/allReady extension methods
- Created ProtoBrowser.scala screen with full navigation
- Added ProtoMessageInfo, ProtoFieldInfo, ProtoBrowserState types
- Added AppScreen.ProtoBrowser and Location.ProtoBrowser
- Extended TypeNarrower with normalizeProtoType(), mapProtoTypeToCanonical(), isProtoTypeCompatible()
- Created BridgeProtoAdapter.scala for Proto→ExternalRecord conversion
- Extended normalizeDbType() to handle Proto scalar and well-known types
- FlowValidator now works with Proto sources via generic SourceEntity abstraction

### 2024-02-08: Avro/Kafka Integration Complete
- Added SpecSourceType.Avro and SourceStatus.ReadyAvro
- Added Avro source loading to SourceLoader
- Added Avro field extraction to ProjectionFieldExtractor
- Created AvroBrowser TUI screen
- Extended TypeNarrower for Avro type compatibility
- Added ExternalRecord abstraction to FileBridgeProjectionMapper
- Created BridgeAvroAdapter for Avro→ExternalRecord conversion
- Extended FlowValidator with proper source type tracking
