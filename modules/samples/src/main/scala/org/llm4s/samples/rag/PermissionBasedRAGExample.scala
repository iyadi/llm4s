package org.llm4s.samples.rag

import org.llm4s.rag.{ EmbeddingProvider, RAG }
import org.llm4s.rag.RAG.RAGConfigOps
import org.llm4s.rag.permissions._
import org.llm4s.rag.permissions.pg.PgSearchIndex
import org.llm4s.config.Llm4sConfig

/**
 * Permission-Based RAG Example
 *
 * This example demonstrates the permission-aware RAG API that enables
 * enterprise-grade access control for document retrieval.
 *
 * Key features:
 * - Hierarchical collection structure (e.g., confluence/EN, confluence/DE)
 * - User and group-based access control
 * - Collection-level queryable_by permissions
 * - Document-level readable_by permissions
 * - Pattern-based collection queries (star, path/star, path/star-star)
 *
 * Prerequisites:
 * - PostgreSQL with pgvector extension
 * - OPENAI_API_KEY environment variable
 *
 * Usage:
 *   # Start PostgreSQL with pgvector
 *   docker run -d --name pgvector -p 5432:5432 \
 *     -e POSTGRES_PASSWORD=postgres \
 *     pgvector/pgvector:pg16
 *
 *   # Run the example
 *   export OPENAI_API_KEY=sk-...
 *   export PGVECTOR_HOST=localhost
 *   export PGVECTOR_PORT=5432
 *   export PGVECTOR_DATABASE=postgres
 *   export PGVECTOR_USER=postgres
 *   export PGVECTOR_PASSWORD=postgres
 *   sbt "samples/runMain org.llm4s.samples.rag.PermissionBasedRAGExample"
 */
object PermissionBasedRAGExample extends App {

  println("=" * 60)
  println("Permission-Based RAG Example")
  println("=" * 60)

  // ========== Part 1: Core Types Overview ==========
  println("\n--- Part 1: Core Types Overview ---")

  // Principal IDs: Positive = User, Negative = Group
  val userId  = PrincipalId.user(42)
  val groupId = PrincipalId.group(5) // Stored as -5

  println(s"User ID: $userId (raw: ${userId.value})")
  println(s"Group ID: $groupId (raw: ${groupId.value})")

  // Collection paths with hierarchy
  val rootPath       = CollectionPath.create("confluence")
  val childPath      = CollectionPath.create("confluence/EN")
  val grandchildPath = CollectionPath.create("confluence/EN/archive")

  println(s"\nCollection paths:")
  rootPath.foreach(p => println(s"  Root: $p (depth: ${p.depth})"))
  childPath.foreach(p => println(s"  Child: $p (depth: ${p.depth})"))
  grandchildPath.foreach { p =>
    println(s"  Grandchild: $p (depth: ${p.depth})")
    println(s"    Parent: ${p.parent.map(_.value).getOrElse("none")}")
  }

  // Collection patterns for querying
  println("\nCollection patterns:")
  println("  * -> matches all collections")
  println("  confluence -> exact match only")
  println("  confluence/★ -> immediate children (EN, DE)")
  println("  confluence/★★ -> all descendants (EN, EN/archive, DE)")

  val patterns = Seq(
    "*"             -> CollectionPath.unsafe("any/path"),
    "confluence"    -> CollectionPath.unsafe("confluence"),
    "confluence/*"  -> CollectionPath.unsafe("confluence/EN"),
    "confluence/**" -> CollectionPath.unsafe("confluence/EN/archive")
  )

  patterns.foreach { case (patternStr, testPath) =>
    CollectionPattern.parse(patternStr).foreach { pattern =>
      val matches = pattern.matches(testPath)
      println(s"  Pattern '$patternStr' matches '$testPath': $matches")
    }
  }

  // User authorization context
  println("\nUser authorization:")
  val auth = UserAuthorization.forUser(
    userId = PrincipalId.user(1),
    groups = Set(PrincipalId.group(10), PrincipalId.group(20))
  )
  println(s"  Principal IDs: ${auth.principalIds.map(_.value).toSeq.sorted}")
  println(s"  Is admin: ${auth.isAdmin}")

  println(s"\nAdmin authorization:")
  println(s"  Bypasses all permission checks: ${UserAuthorization.Admin.isAdmin}")

  // ========== Part 2: API Overview (Without Database) ==========
  println("\n--- Part 2: Permission-Aware RAG API ---")

  println("""
    |// Configure RAG with a SearchIndex for permissions
    |val config = RAG.builder()
    |  .withEmbeddings(EmbeddingProvider.OpenAI)
    |  .withSearchIndex(searchIndex)
    |  .build()
    |
    |// Create collections with access control
    |searchIndex.collections.create(CollectionConfig(
    |  path = CollectionPath.unsafe("internal"),
    |  queryableBy = Set(PrincipalId.group(1)), // Only admins group
    |  isLeaf = true
    |))
    |
    |// Ingest with document-level permissions
    |rag.ingestWithPermissions(
    |  collectionPath = CollectionPath.unsafe("internal"),
    |  documentId = "confidential-doc-1",
    |  content = "Secret company information...",
    |  readableBy = Set(PrincipalId.user(42)) // Only user 42 can read
    |)
    |
    |// Query with permission filtering
    |val results = rag.queryWithPermissions(
    |  auth = UserAuthorization.forUser(PrincipalId.user(42), Set()),
    |  collectionPattern = CollectionPattern.All,
    |  queryText = "company secrets"
    |)
  """.stripMargin)

  // ========== Part 3: Live Demo with PostgreSQL ==========
  println("\n--- Part 3: Live Demo with PostgreSQL ---")

  val pgConfigResult = Llm4sConfig.pgSearchIndex()

  pgConfigResult match {
    case Left(error) =>
      println(s"\nCould not load PostgreSQL config: ${error.message}")
      println("To run the live demo, set env vars like:")
      println("  export PGVECTOR_HOST=localhost")
      println("  export PGVECTOR_PORT=5432")
      println("  export PGVECTOR_DATABASE=postgres")
      println("  export PGVECTOR_USER=postgres")
      println("  export PGVECTOR_PASSWORD=postgres")
      println("\nSkipping live demo...")

    case Right(pgConfig) =>
      val config = pgConfig.copy(vectorTableName = "permission_demo_vectors")
      println(s"PostgreSQL URL: ${config.jdbcUrl}")

      // Try to create a SearchIndex
      PgSearchIndex(config) match {
        case Left(error) =>
          println(s"\nCould not connect to PostgreSQL: ${error.message}")
          println("To run the live demo, start PostgreSQL with pgvector:")
          println("  docker run -d --name pgvector -p 5432:5432 \\")
          println("    -e POSTGRES_PASSWORD=postgres \\")
          println("    pgvector/pgvector:pg16")
          println("\nSkipping live demo...")

        case Right(searchIndex) =>
          println("Connected to PostgreSQL!")

          // Initialize schema
          searchIndex.initializeSchema() match {
            case Left(error) =>
              println(s"Schema initialization failed: ${error.message}")

            case Right(_) =>
              println("Schema initialized.")

              // Create collections
              val collections = searchIndex.collections
              val principals  = searchIndex.principals

              println("\nCreating users and groups...")

              // Create principals
              val johnResult        = principals.getOrCreate(ExternalPrincipal.User("john@example.com"))
              val janeResult        = principals.getOrCreate(ExternalPrincipal.User("jane@example.com"))
              val engineeringResult = principals.getOrCreate(ExternalPrincipal.Group("engineering"))
              val hrResult          = principals.getOrCreate(ExternalPrincipal.Group("hr"))

              (for {
                john        <- johnResult
                jane        <- janeResult
                engineering <- engineeringResult
                hr          <- hrResult
              } yield {
                println(s"  john@example.com -> ${john.value}")
                println(s"  jane@example.com -> ${jane.value}")
                println(s"  engineering group -> ${engineering.value}")
                println(s"  hr group -> ${hr.value}")

                println("\nCreating collections...")

                // Create collection hierarchy
                val publicConfig = CollectionConfig(
                  path = CollectionPath.unsafe("public"),
                  queryableBy = Set.empty, // Empty = public
                  isLeaf = true
                )
                val engineeringConfig = CollectionConfig(
                  path = CollectionPath.unsafe("engineering"),
                  queryableBy = Set(engineering),
                  isLeaf = true
                )
                val hrConfig = CollectionConfig(
                  path = CollectionPath.unsafe("hr"),
                  queryableBy = Set(hr),
                  isLeaf = true
                )

                collections.create(publicConfig).foreach(_ => println("  Created 'public' (accessible to all)"))
                collections
                  .create(engineeringConfig)
                  .foreach(_ => println("  Created 'engineering' (engineering group only)"))
                collections.create(hrConfig).foreach(_ => println("  Created 'hr' (HR group only)"))

                // Demo permission filtering
                println("\nPermission filtering demo:")

                val johnAuth = UserAuthorization.forUser(john, Set(engineering))
                val janeAuth = UserAuthorization.forUser(jane, Set(hr))

                println(s"\n  John (engineering): principals = ${johnAuth.asSeq.sorted}")
                collections.findAccessible(johnAuth, CollectionPattern.All).foreach { accessible =>
                  println(s"    Can access: ${accessible.map(_.path.value).mkString(", ")}")
                }

                println(s"\n  Jane (HR): principals = ${janeAuth.asSeq.sorted}")
                collections.findAccessible(janeAuth, CollectionPattern.All).foreach { accessible =>
                  println(s"    Can access: ${accessible.map(_.path.value).mkString(", ")}")
                }

                println(s"\n  Admin: bypasses all checks")
                collections.findAccessible(UserAuthorization.Admin, CollectionPattern.All).foreach { accessible =>
                  println(s"    Can access: ${accessible.map(_.path.value).mkString(", ")}")
                }

              }) match {
                case Left(error) =>
                  println(s"Error during demo: ${error.message}")
                case Right(_) =>
                  println("\nDemo completed successfully!")
              }

              // Build RAG with SearchIndex
              println("\n--- Building RAG with Permission Support ---")

              val ragResult = for {
                rag <- RAG
                  .builder()
                  .withEmbeddings(EmbeddingProvider.OpenAI)
                  .withSearchIndex(searchIndex)
                  .withTopK(5)
                  .build()
              } yield rag

              ragResult match {
                case Left(error) =>
                  println(s"Could not build RAG: ${error.message}")
                  println("Make sure OPENAI_API_KEY is set for embeddings.")

                case Right(rag) =>
                  println(s"RAG built with permission support!")
                  println(s"  Has permissions: ${rag.hasPermissions}")
                  rag.close()
              }
          }

          searchIndex.close()
      }
  }

  // ========== Part 4: Two-Level Permission Model ==========
  println("\n--- Part 4: Two-Level Permission Model ---")
  println("""
    |Permission filtering happens at two levels:
    |
    |1. COLLECTION-LEVEL (queryable_by):
    |   - Controls who can search within a collection
    |   - Set when creating collection
    |   - Empty array = public (anyone can search)
    |
    |2. DOCUMENT-LEVEL (readable_by):
    |   - Fine-grained control within a collection
    |   - Set when ingesting documents
    |   - Empty array = inherit from collection
    |   - Non-empty = only specified principals can read
    |
    |Example:
    |  Collection: "hr-docs" queryable_by: [hr_group]
    |    - Doc 1: readable_by: [] -> anyone in hr_group can read
    |    - Doc 2: readable_by: [ceo_user] -> only CEO can read
    |
    |Query flow:
    |  1. Filter collections by user's principals (queryable_by check)
    |  2. Vector search within accessible collections
    |  3. Filter results by readable_by check
    |  4. Return permission-filtered results
  """.stripMargin)

  println("\n" + "=" * 60)
  println("Permission-Based RAG Example Complete")
  println("=" * 60)
}
