# Contributing to Viglet Dumont

Thank you for your interest in contributing to Viglet Dumont! This document provides guidelines and instructions for contributing.

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Code Style](#code-style)
- [Commit Guidelines](#commit-guidelines)
- [Pull Request Process](#pull-request-process)
- [Testing](#testing)
- [Documentation](#documentation)

## 📜 Code of Conduct

We are committed to providing a welcoming and inspiring community for all. Please be respectful and constructive in your interactions.

## 🚀 Getting Started

1. Fork the repository on GitHub
2. Clone your fork locally
3. Set up the development environment
4. Create a branch for your work
5. Make your changes
6. Submit a pull request

## 💻 Development Setup

### Prerequisites

- Java 21 or higher
- Maven 3.8+
- Git
- Your favorite IDE (IntelliJ IDEA, Eclipse, or VS Code)

### Setup Steps

```bash
# Clone your fork
git clone https://github.com/YOUR-USERNAME/dumont.git
cd dumont

# Add upstream remote
git remote add upstream https://github.com/openviglet/dumont.git

# Install dependencies and build
mvn clean install

# Run tests
mvn test
```

### IDE Setup

#### IntelliJ IDEA
1. Import as Maven project
2. Enable annotation processing for Lombok
3. Use the code style from `.vscode/eclipse-java-style.xml`

#### Eclipse
1. Import as existing Maven project
2. Install Lombok plugin
3. Apply code style from `.vscode/eclipse-java-style.xml`

#### VS Code
1. Install Java Extension Pack
2. Install Lombok Annotations Support
3. Settings are already configured in `.vscode/settings.json`

## 🤝 How to Contribute

### Reporting Bugs

When reporting bugs, please include:

- A clear and descriptive title
- Steps to reproduce the issue
- Expected behavior
- Actual behavior
- System information (OS, Java version, etc.)
- Relevant logs or screenshots

Use the bug report template in GitHub Issues.

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When suggesting an enhancement:

- Use a clear and descriptive title
- Provide a detailed description of the proposed feature
- Explain why this enhancement would be useful
- List any alternative solutions you've considered

Use the feature request template in GitHub Issues.

### Contributing Code

1. **Find or create an issue**: Before starting work, check if there's an existing issue or create one
2. **Discuss the approach**: For large changes, discuss your approach in the issue first
3. **Fork and branch**: Create a feature branch from `main`
4. **Write code**: Implement your changes following our code style
5. **Write tests**: Add tests for new functionality
6. **Update docs**: Update documentation if needed
7. **Submit PR**: Create a pull request with a clear description

## 📝 Code Style

### Java Code Style

- Follow Java naming conventions
- Use meaningful variable and method names
- Keep methods small and focused (ideally < 30 lines)
- Add JavaDoc comments for public APIs
- Use Lombok annotations where appropriate
- Organize imports (remove unused ones)

### Example

```java
/**
 * Service for managing connector indexing operations.
 * 
 * @author Your Name
 * @since 2026.1.4
 */
@Slf4j
@Service
public class DumConnectorIndexingService {
    
    private final DumConnectorIndexingRepository repository;
    
    /**
     * Constructor with dependency injection.
     * 
     * @param repository the indexing repository
     */
    public DumConnectorIndexingService(DumConnectorIndexingRepository repository) {
        this.repository = repository;
    }
    
    /**
     * Save indexing information for a job item.
     * 
     * @param jobItem the job item to save
     * @param status the indexing status
     */
    public void save(DumJobItemWithSession jobItem, DumIndexingStatus status) {
        log.debug("Saving indexing info for object: {}", jobItem.turSNJobItem().getId());
        DumConnectorIndexingModel indexing = createDumConnectorIndexing(jobItem, status);
        if (indexing != null) {
            repository.save(indexing);
        }
    }
}
```

### Code Formatting

- Use 4 spaces for indentation (no tabs)
- Maximum line length: 120 characters
- Use braces for all control structures
- Add blank lines to separate logical sections

### Lombok Usage

Prefer Lombok annotations:
- `@Data` for POJOs
- `@Builder` for builder pattern
- `@Slf4j` for logging
- `@RequiredArgsConstructor` for constructor injection

## 💬 Commit Guidelines

### Commit Message Format

```
<type>: <subject>

<body>

<footer>
```

#### Types

- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, missing semicolons, etc.)
- `refactor`: Code refactoring (no functionality changes)
- `test`: Adding or updating tests
- `chore`: Maintenance tasks (build, dependencies, etc.)
- `perf`: Performance improvements

#### Examples

```
feat: add Strategy Pattern for indexing providers

Implement IndexingStrategy interface and resolver to support
multiple indexing providers dynamically.

Closes #123
```

```
fix: resolve null pointer exception in indexing service

Add null check before accessing job item metadata.

Fixes #456
```

### Best Practices

- Write clear, concise commit messages
- Use present tense ("Add feature" not "Added feature")
- Reference issues and pull requests when relevant
- Keep commits atomic (one logical change per commit)
- Avoid commits with multiple unrelated changes

## 🔀 Pull Request Process

### Before Submitting

1. **Update your branch**
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run tests**
   ```bash
   mvn clean test
   ```

3. **Check code style**
   ```bash
   mvn checkstyle:check
   ```

4. **Build the project**
   ```bash
   mvn clean install
   ```

### Creating a Pull Request

1. Push your branch to your fork
2. Go to the Dumont repository on GitHub
3. Click "New Pull Request"
4. Select your fork and branch
5. Fill in the PR template:
   - Clear title summarizing the change
   - Description of what changed and why
   - Reference related issues
   - Screenshots (if UI changes)
   - Testing steps

### PR Review Process

1. **Automated checks**: CI/CD pipeline will run tests and checks
2. **Code review**: Maintainers will review your code
3. **Address feedback**: Make requested changes if any
4. **Approval**: Once approved, maintainers will merge

### PR Guidelines

- Keep PRs focused and reasonably sized
- Update documentation if needed
- Add tests for new features
- Ensure all tests pass
- Respond to review comments promptly
- Be open to feedback and suggestions

## 🧪 Testing

### Writing Tests

- Write unit tests for all new code
- Use descriptive test method names
- Follow AAA pattern (Arrange, Act, Assert)
- Mock external dependencies
- Aim for >80% code coverage

### Test Example

```java
@ExtendWith(MockitoExtension.class)
class DumConnectorIndexingServiceTest {
    
    @Mock
    private DumConnectorIndexingRepository repository;
    
    @InjectMocks
    private DumConnectorIndexingService service;
    
    @Test
    @DisplayName("Should save indexing model successfully")
    void testSaveIndexingModel() {
        // Arrange
        DumJobItemWithSession jobItem = createTestJobItem();
        
        // Act
        service.save(jobItem, DumIndexingStatus.INDEXED);
        
        // Assert
        verify(repository, times(1)).save(any(DumConnectorIndexingModel.class));
    }
    
    private DumJobItemWithSession createTestJobItem() {
        // Test data setup
        return DumJobItemWithSession.builder()
            .turSNJobItem(new TurSNJobItem())
            .session(new DumConnectorSession())
            .build();
    }
}
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=DumConnectorIndexingServiceTest

# Run with coverage report
mvn clean test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

## 📚 Documentation

### When to Update Documentation

Update documentation when:
- Adding new features
- Changing existing functionality
- Adding new configuration options
- Modifying APIs

### Documentation Types

1. **Code Documentation**
   - JavaDoc for public APIs
   - Inline comments for complex logic
   - README files for modules

2. **User Documentation**
   - Update README.md
   - Add examples to `/docs/examples/`
   - Update configuration guides

3. **API Documentation**
   - Add OpenAPI/Swagger annotations
   - Update API examples

### Documentation Example

```java
/**
 * Validates source content against indexed content.
 * 
 * <p>This method compares the content in the source system with what's
 * currently indexed and returns any discrepancies.</p>
 * 
 * @param source the source system identifier
 * @return validation result with missing and extra content
 * @throws ValidationException if the source is invalid
 * @since 2026.1.4
 */
@GetMapping("validate/{source}")
public DumConnectorValidateDifference validateSource(@PathVariable String source) {
    return DumConnectorValidateDifference.builder()
        .missing(dumConnectorSolr.solrMissingContent(source, plugin.getProviderName()))
        .extra(dumConnectorSolr.solrExtraContent(source, plugin.getProviderName()))
        .build();
}
```

## 🏆 Recognition

Contributors will be:
- Listed in the project README
- Mentioned in release notes
- Credited in commit history
- Appreciated by the community!

## 💡 Need Help?

- 📖 Check the [documentation](README.md)
- 💬 Ask in [GitHub Discussions](https://github.com/openviglet/dumont/discussions)
- 🐛 Search [existing issues](https://github.com/openviglet/dumont/issues)
- 📧 Email: support@viglet.com

## 📜 License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.

---

Thank you for contributing to Viglet Dumont! 🎉
