# Duplicate File Finder

A powerful Java application that finds and manages duplicate files using both exact content matching (SHA-256) and visual similarity detection (dHash) for images. Cross-platform compatible with Windows, Mac, and Linux.

## Features

### Core Functionality
- **Exact Duplicate Detection**: Finds files with identical content using SHA-256 hashing
- **Visual Duplicate Detection**: Identifies visually similar images using dHash (difference hash) algorithm
- **Recursive Scanning**: Searches through all subfolders in the selected directory
- **Cross-Platform**: Works on Windows, Mac, and Linux systems

### User Interface
- **Intuitive GUI**: Easy-to-use graphical interface with folder selection
- **Dual Detection Display**: Separate sections for exact duplicates vs visual duplicates
- **Interactive File Management**: Checkboxes for selecting files to delete
- **Clickable File Names**: Click any file name to open its location in the system file explorer
- **Smart Pre-selection**: Automatically selects duplicates for deletion while keeping the first file in each group

### Advanced Features
- **Comprehensive Logging**: Collapsible log panel showing all scanned files with their hashes
- **Export Functionality**: Export detailed logs to timestamped text files
- **Real-time Updates**: Log updates instantly as you check/uncheck files
- **Progress Feedback**: Live progress updates during scanning and analysis
- **Batch Operations**: Select All/Deselect All buttons for quick bulk operations

### File Management
- **Safe Deletion**: Multiple confirmation dialogs prevent accidental deletions
- **Error Handling**: Comprehensive error reporting for permission issues or corrupted files
- **Status Tracking**: Clear indication of which files are selected for deletion vs keeping

## System Requirements

### Minimum Requirements
- **Java**: Java 8 (JRE 1.8) or higher
- **Operating System**: Windows 10, macOS 10.12, or Linux (Ubuntu 16.04 equivalent)
- **Memory**: 512 MB RAM minimum
- **Storage**: 50 MB free disk space

### Recommended Requirements
- **Java**: Java 11 or higher for optimal performance
- **Memory**: 2 GB RAM for processing large directories
- **Storage**: 100 MB free disk space for logs and temporary files

### Supported Image Formats
- **Exact Duplicates**: All file types
- **Visual Duplicates**: JPG, JPEG, PNG, GIF, BMP, TIFF, TIF, WEBP

## Installation

### Option 1: Run the JAR File (Recommended)
1. Ensure Java JRE 8+ is installed on your system
2. Download the `DuplicateFileFinder.jar` file
3. **Windows**: Double-click the JAR file, or run `java -jar DuplicateFileFinder.jar` in command prompt
4. **Mac/Linux**: Run `java -jar DuplicateFileFinder.jar` in terminal
5. **Alternative**: Use the provided scripts:
   - **Windows**: Double-click `run.bat`
   - **Mac/Linux**: Run `./run.sh` in terminal

### Option 2: Direct Compilation
1. Ensure Java JDK 8+ is installed on your system
2. Download or clone the `DuplicateFileFinder.java` file
3. Open terminal/command prompt in the file directory
4. Compile: `javac DuplicateFileFinder.java`
5. Run: `java DuplicateFileFinder`

### Option 3: Using an IDE
1. Open your Java IDE (Eclipse, IntelliJ IDEA, NetBeans, etc.)
2. Create a new Java project
3. Import the `DuplicateFileFinder.java` file
4. Run the main class

## How to Use

### Basic Operation
1. **Launch** the application:
   - **JAR file**: Run `java -jar DuplicateFileFinder.jar` or double-click the JAR file
   - **Source code**: Run `java DuplicateFileFinder`
2. **Select Folder** using the "Browse" button to choose the directory to scan
3. **Find Duplicates** by clicking the "Find Duplicates" button
4. **Review Results** in two sections:
   - **Blue Section**: Exact duplicates (identical file content)
   - **Green Section**: Visual duplicates (similar-looking images)
5. **Select Files** using checkboxes next to files you want to delete
6. **Delete** selected files using the "Delete Selected Duplicates" button

### Advanced Features

#### Using the Log
1. **Show Log**: Click "Show Log" to view the collapsible log panel
2. **Review All Files**: The log shows every scanned file with status and hashes
3. **Export Log**: Click "Export Log" to save a detailed report to a text file

#### File Status in Log
- **[CHECKED]**: Files selected for deletion
- **[UNCHECKED]**: Duplicate files not selected (will be kept)
- **[UNIQUE]**: Files with no duplicates found

#### Understanding Hash Information
- **HASH**: SHA-256 content hash (same for identical files)
- **DHASH**: Perceptual hash for images (similar for visually similar images)

### Best Practices

#### Before Scanning
- **Backup Important Data**: Always backup important files before running duplicate detection
- **Close Other Applications**: Close programs that might be accessing files in the target directory
- **Check Permissions**: Ensure you have read/write access to the target directory

#### During Review
- **Verify Before Deleting**: Always review the duplicate groups before deletion
- **Keep Original Files**: The first file in each group is recommended to keep (unchecked by default)
- **Check File Paths**: Use the clickable file names to verify file locations

#### For Large Directories
- **Expect Processing Time**: Large directories with many images may take several minutes
- **Monitor Progress**: Watch the progress messages during scanning
- **Check Available Space**: Ensure sufficient disk space for log files

## How It Works

### Exact Duplicate Detection
1. **File Scanning**: Recursively scans all files in the selected directory
2. **Size Grouping**: Groups files by size for performance optimization
3. **Hash Calculation**: Calculates SHA-256 hash for files with matching sizes
4. **Duplicate Identification**: Files with identical hashes are exact duplicates

### Visual Duplicate Detection (Images Only)
1. **Image Filtering**: Identifies image files by extension
2. **dHash Calculation**: 
   - Resizes images to 9x8 pixels in grayscale
   - Compares adjacent pixel brightness
   - Creates a 64-bit perceptual hash
3. **Similarity Matching**: Uses Hamming distance ≤ 5 to find similar images
4. **Grouping**: Groups visually similar images together

### Priority System
- **Exact duplicates** take priority over visual duplicates
- **Images found as exact duplicates** won't appear in visual duplicates
- **Each file appears only once** in the results

## Troubleshooting

### Common Issues

#### "No files found in the selected folder"
- **Check permissions**: Ensure read access to the folder
- **Verify path**: Make sure the folder path is correct and exists
- **Check for hidden files**: Some systems hide certain file types

#### "Error calculating hash for file"
- **File in use**: Close applications that might be using the file
- **Corrupted file**: The file may be damaged or inaccessible
- **Permission denied**: Ensure read permissions for the file

#### Slow performance on large directories
- **Expected behavior**: Large directories take time to process
- **Memory issues**: Increase Java heap size: `java -Xmx2g DuplicateFileFinder`
- **Close other apps**: Free up system resources

#### Visual duplicates not detected
- **Check image format**: Ensure images are in supported formats
- **Image corruption**: Some images may be unreadable
- **Already exact duplicates**: Images found as exact duplicates won't appear in visual duplicates

### Error Messages

#### "Could not read image"
- **Unsupported format**: Image format may not be supported
- **Corrupted image**: The image file may be damaged
- **Access denied**: Check file permissions

#### "Error walking directory tree"
- **Permission denied**: Insufficient access to scan the directory
- **Network issues**: If scanning network drives, check connectivity
- **Path too long**: Some systems have path length limitations

## Technical Details

### Algorithms Used
- **SHA-256**: Cryptographic hash function for exact duplicate detection
- **dHash**: Difference hash algorithm for perceptual image similarity
- **Hamming Distance**: Measures similarity between dHash values

### Performance Characteristics
- **Time Complexity**: O(n log n) for sorting + O(n) for hashing
- **Space Complexity**: O(n) for storing file information and hashes
- **Scalability**: Handles thousands of files efficiently

### Security Considerations
- **Local Processing**: All analysis performed locally, no data transmitted
- **Safe Deletion**: Multiple confirmations prevent accidental file loss
- **Read-Only Scanning**: Initial scan only reads files, doesn't modify them

## Contributing

This is an open-source project. Feel free to contribute improvements, bug fixes, or additional features.

### Development Setup
1. Clone the repository
2. Ensure Java JDK 11+ is installed
3. Import into your preferred IDE
4. Run tests and build

### Reporting Issues
When reporting bugs, please include:
- Operating system and version
- Java version
- Steps to reproduce the issue
- Error messages (if any)
- Sample directory structure (if relevant)

## License

This project is open source. Please check the repository for specific license terms.

## Version History

### Current Version
- Exact duplicate detection using SHA-256
- Visual duplicate detection using dHash
- Cross-platform file explorer integration
- Comprehensive logging and export functionality
- Interactive GUI with real-time updates

---

**Note**: Always backup important files before using any duplicate file management tool. This application permanently deletes selected files and cannot recover them once deleted.