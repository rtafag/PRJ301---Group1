const fs = require('fs');
const path = require('path');
const archiver = require(path.join(__dirname, 'portal-node', 'node_modules', 'archiver'));

const outputPptx = path.join(__dirname, 'AITA_Group1_Final_Presentation.pptx');
const outputSlides = path.join(__dirname, 'slides', 'AITA_Group1_Final_Presentation.pptx');
const sourceDir = path.join(__dirname, 'template_extracted');

const output = fs.createWriteStream(outputPptx);
const archive = archiver('zip', { zlib: { level: 9 } });

output.on('close', function() {
  console.log(`Successfully packaged ${archive.pointer()} total bytes to ${outputPptx}`);
  fs.copyFileSync(outputPptx, outputSlides);
  console.log(`Copied to ${outputSlides}`);
});

archive.on('error', function(err) {
  throw err;
});

archive.pipe(output);

// Append all files from template_extracted recursively with forward slashes
function addDirectory(currentDir, zipPrefix) {
  const files = fs.readdirSync(currentDir);
  for (const file of files) {
    const fullPath = path.join(currentDir, file);
    const stat = fs.statSync(fullPath);
    const zipPath = zipPrefix ? `${zipPrefix}/${file}` : file;
    if (stat.isDirectory()) {
      addDirectory(fullPath, zipPath);
    } else {
      archive.file(fullPath, { name: zipPath });
    }
  }
}

addDirectory(sourceDir, '');
archive.finalize();
